package com.qrmenu.ownernotification;

import com.fasterxml.jackson.databind.JsonNode;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.qrmenu.dailyclose.DailyBranchCloseReport;
import com.qrmenu.dailyclose.DailyCloseService;
import com.qrmenu.ownernotification.repository.OwnerNotificationLogRepository;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Rapor Bildirimleri" ekranı (Raporlar sayfası): DAILY+MONTHLY birleşik geçmiş listesi ve
 * MONTHLY için yeni manuel resend endpoint'i. Kendi GreenMail sunucusunu ayrı (otomatik) bir
 * portta başlatır - {@link OwnerNotificationFlowIntegrationTest} (sabit port 3025) ve {@link
 * MonthlyReportFlowIntegrationTest} ile aynı anda çakışmasın diye.
 */
class ReportNotificationIntegrationTest extends AbstractIntegrationTest {

    private static final GreenMail GREEN_MAIL = new GreenMail(new ServerSetup(0, null, ServerSetup.PROTOCOL_SMTP));

    static {
        GREEN_MAIL.start();
    }

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
    }

    @Autowired
    private DailyCloseService dailyCloseService;

    @Autowired
    private OwnerNotificationService ownerNotificationService;

    @Autowired
    private OwnerNotificationLogRepository logRepository;

    @BeforeEach
    void resetMailbox() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    @Test
    void manualMonthlyResendSendsFreshAttemptEvenAfterAutoAlreadySent() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Notif Business 1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "report-notif-admin-1@example.com");
        createContact(businessId, adminCookie, "owner-rn-1@example.com", true);

        YearMonth periodMonth = YearMonth.of(2026, 2);
        ownerNotificationService
                .dispatchAutoForMonthlyReport(UUID.fromString(businessId), UUID.fromString(branchId), periodMonth)
                .get(5, TimeUnit.SECONDS);
        assertThat(ownerNotificationService.listForMonthlyPeriod(UUID.fromString(branchId), periodMonth)).hasSize(1);

        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        JsonNode resendResult = objectMapper.readTree(mockMvc.perform(post(
                                "/api/staff/branches/{branchId}/reports/monthly/resend", branchId)
                        .param("period", "2026-02")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(resendResult).hasSize(2);
        assertThat(resendResult.get(0).get("triggeredBy").asText()).isEqualTo("AUTO");
        assertThat(resendResult.get(1).get("triggeredBy").asText()).isEqualTo("MANUAL");
        assertThat(resendResult.get(1).get("reportType").asText()).isEqualTo("MONTHLY");
        assertThat(resendResult.get(1).get("period").asText()).isEqualTo("2026-02-01");
        assertThat(ownerNotificationService.listForMonthlyPeriod(UUID.fromString(branchId), periodMonth)).hasSize(2);
    }

    @Test
    void manualMonthlyResendRetriesImmediatelyAfterFailureWithoutWaitingForBackoff() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Notif Business 2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "report-notif-admin-2@example.com");
        String contactId = createContact(businessId, adminCookie, "owner-rn-2@example.com", true);
        YearMonth periodMonth = YearMonth.of(2026, 3);
        LocalDate period = periodMonth.atDay(1);

        // Manufactures a FAILED attempt from 10 minutes ago directly - MonthlyReportContactDispatcher's
        // own AUTO backoff (1h) would still be skipping this contact right now; a manual "Yeniden
        // Dene" click must ignore that entirely and retry immediately. (Not using a real
        // GreenMail stop/restart here - MonthlyReportFlowIntegrationTest's javadoc documents why
        // restarting-then-immediately-reusing the server within the same test would be flaky.)
        OwnerNotificationLog recentFailure = newMonthlyLog(
                businessId, branchId, contactId, "owner-rn-2@example.com", period,
                OwnerNotificationStatus.FAILED, Instant.now().minus(Duration.ofMinutes(10)));
        logRepository.saveAndFlush(recentFailure);
        assertThat(ownerNotificationService.listForMonthlyPeriod(UUID.fromString(branchId), periodMonth)).hasSize(1);

        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        JsonNode resendResult = objectMapper.readTree(mockMvc.perform(post(
                                "/api/staff/branches/{branchId}/reports/monthly/resend", branchId)
                        .param("period", "2026-03")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(resendResult).hasSize(2);
        assertThat(resendResult.get(1).get("status").asText()).isEqualTo("SENT");
        assertThat(resendResult.get(1).get("triggeredBy").asText()).isEqualTo("MANUAL");
    }

    @Test
    void unifiedNotificationListShowsBothDailyAndMonthlyWithCorrectPeriod() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Notif Business 3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "report-notif-admin-3@example.com");
        createContact(businessId, adminCookie, "owner-rn-3@example.com", true);

        LocalDate businessDate = LocalDate.now(ZoneOffset.UTC);
        DailyBranchCloseReport dailyReport = dailyCloseService.generateFinal(
                UUID.fromString(businessId), UUID.fromString(branchId), businessDate);
        ownerNotificationService.dispatchAutoForDailyClose(dailyReport).get(5, TimeUnit.SECONDS);

        YearMonth periodMonth = YearMonth.of(2026, 4);
        ownerNotificationService
                .dispatchAutoForMonthlyReport(UUID.fromString(businessId), UUID.fromString(branchId), periodMonth)
                .get(5, TimeUnit.SECONDS);

        JsonNode list = objectMapper.readTree(mockMvc.perform(get(
                                "/api/staff/branches/{branchId}/reports/notifications", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(list).hasSize(2);
        JsonNode dailyEntry = findByReportType(list, "DAILY");
        JsonNode monthlyEntry = findByReportType(list, "MONTHLY");
        assertThat(dailyEntry.get("period").asText()).isEqualTo(businessDate.toString());
        assertThat(dailyEntry.get("recipientEmail").asText()).isEqualTo("owner-rn-3@example.com");
        assertThat(monthlyEntry.get("period").asText()).isEqualTo("2026-04-01");
        assertThat(monthlyEntry.get("recipientEmail").asText()).isEqualTo("owner-rn-3@example.com");
    }

    /** Same reflection idiom as {@code MonthlyReportFlowIntegrationTest.newMonthlyLog} - the constructor is package-private. */
    private OwnerNotificationLog newMonthlyLog(
            String businessId,
            String branchId,
            String contactId,
            String email,
            LocalDate period,
            OwnerNotificationStatus status,
            Instant attemptedAt) {
        try {
            var ctor = OwnerNotificationLog.class.getDeclaredConstructor(
                    UUID.class,
                    UUID.class,
                    UUID.class,
                    UUID.class,
                    String.class,
                    OwnerNotificationChannel.class,
                    OwnerNotificationStatus.class,
                    String.class,
                    OwnerNotificationTrigger.class,
                    UUID.class,
                    Instant.class,
                    OwnerNotificationReportType.class,
                    LocalDate.class);
            ctor.setAccessible(true);
            return ctor.newInstance(
                    null,
                    UUID.fromString(businessId),
                    UUID.fromString(branchId),
                    UUID.fromString(contactId),
                    email,
                    OwnerNotificationChannel.EMAIL,
                    status,
                    null,
                    OwnerNotificationTrigger.AUTO,
                    null,
                    attemptedAt,
                    OwnerNotificationReportType.MONTHLY,
                    period);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode findByReportType(JsonNode list, String reportType) {
        for (JsonNode entry : list) {
            if (entry.get("reportType").asText().equals(reportType)) {
                return entry;
            }
        }
        throw new AssertionError("No entry found for reportType=" + reportType + " in " + list);
    }

    private String createContact(String businessId, String staffCookie, String email, boolean monthlyReportRecipient)
            throws Exception {
        String body = "{\"name\":\"Sahip\",\"phone\":null,\"email\":\"" + email + "\",\"whatsappEnabled\":false,"
                + "\"dailyReportRecipient\":true,\"monthlyReportRecipient\":" + monthlyReportRecipient + "}";
        return objectMapper
                .readTree(mockMvc.perform(post("/api/staff/business/contacts")
                                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
    }
}
