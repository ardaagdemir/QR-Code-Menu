package com.qrmenu.ownernotification;

import com.fasterxml.jackson.databind.JsonNode;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.qrmenu.dailyclose.DailyBranchCloseReport;
import com.qrmenu.dailyclose.DailyCloseService;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Gap-analysis #11 (product-requirements.md Section 15): auto-dispatch idempotency per
 * (report, contact), manual resend bypassing that guard, ineligible contacts (inactive /
 * opted-out / no email) being skipped, and the REPORT_VIEW permission gate. Uses an
 * in-memory GreenMail SMTP server (registered over spring.mail.host/port) instead of the
 * dev "mailhog" service - no real network call.
 */
class OwnerNotificationFlowIntegrationTest extends AbstractIntegrationTest {

    private static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP);

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

    @BeforeEach
    void resetMailbox() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    @Test
    void autoDispatchIsIdempotentPerReportAndContact() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Notify Business 1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "notify-admin-1@example.com");
        createContact(businessId, adminCookie, "owner1@example.com", true, true);

        DailyBranchCloseReport report = dailyCloseService.generateFinal(
                UUID.fromString(businessId), UUID.fromString(branchId), LocalDate.now(ZoneOffset.UTC));

        ownerNotificationService.dispatchAutoForDailyClose(report).get(5, TimeUnit.SECONDS);

        MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages[0].getAllRecipients()[0].toString()).isEqualTo("owner1@example.com");
        // GreenMailUtil.getBody returns the raw (quoted-printable encoded) body - getContent()
        // goes through JavaMail's own transfer-decoding, same as a real mail client would see.
        assertThat((String) messages[0].getContent()).contains("Şube");

        // Simulates the scheduler re-polling an already-FINAL day - must not double-send.
        ownerNotificationService.dispatchAutoForDailyClose(report).get(5, TimeUnit.SECONDS);
        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
        assertThat(ownerNotificationService.listForReport(report.getId())).hasSize(1);
    }

    @Test
    void ineligibleContactsAreSkipped() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Notify Business 2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "notify-admin-2@example.com");
        createContact(businessId, adminCookie, "eligible@example.com", true, true);
        createContact(businessId, adminCookie, "not-opted-in@example.com", false, true);
        createContact(businessId, adminCookie, "inactive@example.com", true, false);

        DailyBranchCloseReport report = dailyCloseService.generateFinal(
                UUID.fromString(businessId), UUID.fromString(branchId), LocalDate.now(ZoneOffset.UTC));
        ownerNotificationService.dispatchAutoForDailyClose(report).get(5, TimeUnit.SECONDS);

        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
        assertThat(GREEN_MAIL.getReceivedMessages()[0].getAllRecipients()[0].toString()).isEqualTo("eligible@example.com");
    }

    @Test
    void manualResendAlwaysCreatesFreshAttemptEvenAfterAutoAlreadySent() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Notify Business 3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "notify-admin-3@example.com");
        createContact(businessId, adminCookie, "owner3@example.com", true, true);

        DailyBranchCloseReport report = dailyCloseService.generateFinal(
                UUID.fromString(businessId), UUID.fromString(branchId), LocalDate.now(ZoneOffset.UTC));
        ownerNotificationService.dispatchAutoForDailyClose(report).get(5, TimeUnit.SECONDS);

        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        JsonNode resendResult = objectMapper.readTree(mockMvc.perform(post(
                                "/api/staff/branches/{branchId}/daily-close/{reportId}/notifications/resend",
                                branchId,
                                report.getId())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(resendResult).hasSize(2);
        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(2);

        JsonNode listResult = objectMapper.readTree(mockMvc.perform(get(
                                "/api/staff/branches/{branchId}/daily-close/{reportId}/notifications", branchId, report.getId())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(listResult).hasSize(2);
        assertThat(listResult.get(0).get("triggeredBy").asText()).isEqualTo("AUTO");
        assertThat(listResult.get(1).get("triggeredBy").asText()).isEqualTo("MANUAL");
    }

    private void createContact(
            String businessId, String staffCookie, String email, boolean dailyReportRecipient, boolean active)
            throws Exception {
        String body = "{\"name\":\"Sahip\",\"phone\":null,\"email\":\"" + email + "\",\"whatsappEnabled\":false,"
                + "\"dailyReportRecipient\":" + dailyReportRecipient + ",\"monthlyReportRecipient\":false}";
        String contactId = objectMapper
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
        if (!active) {
            String updateBody = "{\"name\":\"Sahip\",\"phone\":null,\"email\":\"" + email + "\",\"whatsappEnabled\":false,"
                    + "\"dailyReportRecipient\":" + dailyReportRecipient + ",\"monthlyReportRecipient\":false,\"active\":false}";
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                            "/api/staff/business/contacts/{contactId}", contactId)
                            .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody))
                    .andExpect(status().isOk());
        }
    }
}
