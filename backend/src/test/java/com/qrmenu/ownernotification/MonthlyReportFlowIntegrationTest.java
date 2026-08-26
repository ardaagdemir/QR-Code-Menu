package com.qrmenu.ownernotification;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.qrmenu.dailyclose.DailyBranchCloseReport;
import com.qrmenu.dailyclose.DailyCloseService;
import com.qrmenu.ownernotification.repository.OwnerNotificationLogRepository;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.tenant.BusinessContact;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aylık rapor gönderimi: {@link MonthlyReportScheduler} branch timezone tetiklemesi, {@link
 * OwnerNotificationService#dispatchAutoForMonthlyReport} duplicate-önleme/backoff davranışı ve
 * {@link MonthlyReportContactDispatcher}'ın DB-seviyeli concurrency-safe idempotency garantisi.
 * Kendi GreenMail sunucusunu port 0 (otomatik boş port) ile başlatır - {@link
 * OwnerNotificationFlowIntegrationTest}'in sabit test portuyla (3025) çakışmasın diye.
 */
class MonthlyReportFlowIntegrationTest extends AbstractIntegrationTest {

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
    private MonthlyReportScheduler monthlyReportScheduler;

    @Autowired
    private MonthlyReportContactDispatcher monthlyReportContactDispatcher;

    @Autowired
    private OwnerNotificationLogRepository logRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetMailbox() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    @Test
    void schedulerOnlyDispatchesForBranchWhoseLocalDateIsFirstOfMonth() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Monthly Business 1");
        String firstBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "İstanbul Şube");
        String notYetBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Baker Adası Şube");
        String firstAdminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, firstBranchId, "monthly-admin-1a@example.com");
        String notYetAdminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, notYetBranchId, "monthly-admin-1b@example.com");
        setBranchTimezone(firstBranchId, firstAdminCookie, "Europe/Istanbul");
        setBranchTimezone(notYetBranchId, notYetAdminCookie, "Etc/GMT+12");
        // Contacts are business-scoped (not per-branch), so this one contact is a recipient
        // candidate for BOTH branches - the assertion below therefore proves the scheduler
        // itself skipped the not-yet-due branch, not merely that a contact opted out of it.
        createContact(businessId, firstAdminCookie, "owner-monthly-1@example.com", true);

        // 2026-03-01T00:30:00Z: Europe/Istanbul (UTC+3) is already 2026-03-01 (day 1) -> due.
        // Etc/GMT+12 (UTC-12) is still 2026-02-28 -> not due yet. NOTE: AbstractIntegrationTest's
        // Postgres container - and its data - is shared across every test class in the run
        // (singleton pattern, see its class javadoc), and every other branch created anywhere in
        // the suite defaults to Europe/Istanbul too - so run() legitimately dispatches to
        // unrelated leftover contacts from other tests on this same fixed instant. Assertions
        // below filter to this test's own contact/branches instead of the raw global mailbox.
        Instant now = Instant.parse("2026-03-01T00:30:00Z");
        monthlyReportScheduler.run(now);
        waitForMessagesTo("owner-monthly-1@example.com", 1);

        YearMonth expectedPeriod = YearMonth.of(2026, 2);
        assertThat(findMonthlyLogs(firstBranchId, expectedPeriod)).hasSize(1);
        assertThat(findMonthlyLogs(notYetBranchId, expectedPeriod)).isEmpty();
    }

    @Test
    void autoDispatchIsIdempotentPerBranchPeriodAndContact() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Monthly Business 2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "monthly-admin-2@example.com");
        createContact(businessId, adminCookie, "owner-monthly-2@example.com", true);

        YearMonth periodMonth = YearMonth.of(2026, 2);
        ownerNotificationService
                .dispatchAutoForMonthlyReport(UUID.fromString(businessId), UUID.fromString(branchId), periodMonth)
                .get(5, TimeUnit.SECONDS);
        assertThat(messagesTo("owner-monthly-2@example.com")).hasSize(1);

        // Aynı ay için ikinci çağrı - günlükteki "scheduler'ın her 5 dakikada bir aynı FINAL
        // günü tekrar poll etmesi" senaryosunun aylık karşılığı - duplicate göndermemeli.
        ownerNotificationService
                .dispatchAutoForMonthlyReport(UUID.fromString(businessId), UUID.fromString(branchId), periodMonth)
                .get(5, TimeUnit.SECONDS);
        assertThat(messagesTo("owner-monthly-2@example.com")).hasSize(1);
        assertThat(findMonthlyLogs(branchId, periodMonth)).hasSize(1);
    }

    /**
     * Stops the in-memory SMTP server before dispatch so the real send call throws (connection
     * refused) - mirrors {@code OwnerNotificationFlowIntegrationTest.failedSmtpDeliveryIsRecordedAsFailedWithErrorMessage}:
     * a single stop/attempt/restart, never restarting-then-immediately-reusing the server within
     * the same test (GreenMail's listener isn't guaranteed to be ready the instant {@code start()}
     * returns, so a second attempt moments later would be a flaky test, not a real assertion).
     */
    @Test
    void genuineSmtpFailureIsRecordedAsFailedWithErrorMessage() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Monthly Business 3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "monthly-admin-3@example.com");
        createContact(businessId, adminCookie, "owner-monthly-3@example.com", true);
        YearMonth periodMonth = YearMonth.of(2026, 2);

        GREEN_MAIL.stop();
        try {
            ownerNotificationService
                    .dispatchAutoForMonthlyReport(UUID.fromString(businessId), UUID.fromString(branchId), periodMonth)
                    .get(5, TimeUnit.SECONDS);
        } finally {
            GREEN_MAIL.start();
        }

        List<OwnerNotificationLog> logs = findMonthlyLogs(branchId, periodMonth);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(OwnerNotificationStatus.FAILED);
        assertThat(logs.get(0).getErrorMessage()).isNotBlank();
    }

    /**
     * Backoff *timing* decision, isolated from real SMTP mechanics: a FAILED attempt is
     * manufactured directly (bypassing the mail server entirely) at a known {@code attemptedAt},
     * so the test controls precisely how "old" it is instead of racing a real GreenMail
     * stop/restart cycle.
     */
    @Test
    void failedDeliveryIsRetriedOnlyAfterBackoffWindow() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Monthly Business 7");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "monthly-admin-7@example.com");
        String contactId = createContact(businessId, adminCookie, "owner-monthly-7@example.com", true);
        YearMonth periodMonth = YearMonth.of(2026, 2);
        LocalDate period = periodMonth.atDay(1);

        OwnerNotificationLog recentFailure = newMonthlyLog(
                businessId,
                branchId,
                contactId,
                "owner-monthly-7@example.com",
                period,
                OwnerNotificationStatus.FAILED,
                Instant.now().minus(Duration.ofMinutes(10)));
        logRepository.saveAndFlush(recentFailure);

        // Backoff penceresi (1 saat) içinde - başarılı olurdu olsa bile spam'i önlemek için hiç
        // denenmemeli.
        ownerNotificationService
                .dispatchAutoForMonthlyReport(UUID.fromString(businessId), UUID.fromString(branchId), periodMonth)
                .get(5, TimeUnit.SECONDS);
        assertThat(messagesTo("owner-monthly-7@example.com")).isEmpty();
        assertThat(findMonthlyLogs(branchId, periodMonth)).hasSize(1);

        // Son denemenin zamanını backoff penceresinin dışına (2 saat öncesine) taşı - gerçek
        // zamanın geçmesini beklemek yerine testte deterministik şekilde simüle ediyoruz.
        pushAttemptedAtBack(recentFailure.getId(), Instant.now().minus(Duration.ofHours(2)));

        ownerNotificationService
                .dispatchAutoForMonthlyReport(UUID.fromString(businessId), UUID.fromString(branchId), periodMonth)
                .get(5, TimeUnit.SECONDS);
        assertThat(messagesTo("owner-monthly-7@example.com")).hasSize(1);
        List<OwnerNotificationLog> afterRetry = findMonthlyLogs(branchId, periodMonth);
        assertThat(afterRetry).hasSize(2);
        assertThat(afterRetry.get(1).getStatus()).isEqualTo(OwnerNotificationStatus.SENT);
    }

    /**
     * V38'deki case-insensitive email index'iyle aynı desen: app-katmanı (advisory lock +
     * backoff kontrolü) artı bu partial unique index, aynı (branch, ay, kişi) için iki SENT+AUTO
     * satırının fiziksel olarak var olamayacağını DB seviyesinde garanti eder.
     */
    @Test
    void monthlyAutoSentUniqueIndexRejectsSecondSentRowForSameBranchPeriodContact() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Monthly Business 4");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "monthly-admin-4@example.com");
        String contactId = createContact(businessId, adminCookie, "owner-monthly-4@example.com", true);
        LocalDate period = LocalDate.of(2026, 2, 1);

        OwnerNotificationLog first = newMonthlyLog(
                businessId, branchId, contactId, "owner-monthly-4@example.com", period, OwnerNotificationStatus.SENT, Instant.now());
        logRepository.saveAndFlush(first);

        OwnerNotificationLog second = newMonthlyLog(
                businessId, branchId, contactId, "owner-monthly-4@example.com", period, OwnerNotificationStatus.SENT, Instant.now());
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> logRepository.saveAndFlush(second));
    }

    /**
     * Gerçek eşzamanlılık: aynı (branch, ay, kişi) için birden çok thread aynı anda
     * {@link MonthlyReportContactDispatcher#attemptForContact} çağırır - {@code
     * pg_try_advisory_xact_lock} sayesinde sadece biri gerçekten gönderir, diğerleri kilidi
     * alamayıp hemen döner. İki scheduler instance'ının aynı 5 dakikalık tick'te çakışması
     * senaryosunun testte simülasyonu.
     */
    @Test
    void concurrentAttemptsForSameContactSendOnlyOneEmail() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Monthly Business 5");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "monthly-admin-5@example.com");
        String contactId = createContact(businessId, adminCookie, "owner-monthly-5@example.com", true);
        YearMonth periodMonth = YearMonth.of(2026, 2);
        BusinessContact contact = entityManager.find(BusinessContact.class, UUID.fromString(contactId));

        int threadCount = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    monthlyReportContactDispatcher.attemptForContact(
                            UUID.fromString(businessId),
                            UUID.fromString(branchId),
                            periodMonth,
                            contact,
                            "Aylık Rapor Test",
                            "Test gövde");
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(messagesTo("owner-monthly-5@example.com")).hasSize(1);
        List<OwnerNotificationLog> logs = findMonthlyLogs(branchId, periodMonth);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(OwnerNotificationStatus.SENT);
    }

    /**
     * V40'ın report_type/report_period backfill'i - mevcut (V40'tan önceki şekliyle) DAILY
     * satırlar report_type/report_period kolonlarını hiç bilmez; ALTER TABLE ... DEFAULT 'DAILY'
     * bu satırları geriye dönük doldurur. Bunu, o eski kolon setini kullanan ham bir INSERT ile
     * simüle edip DEFAULT'un gerçekten uygulandığını doğruluyoruz.
     */
    @Test
    @Transactional
    void v40MigrationBackfillsExistingDailyRowsWithDailyReportTypeAndNullPeriod() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Monthly Business 6");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "monthly-admin-6@example.com");
        String contactId = createContact(businessId, adminCookie, "owner-monthly-6@example.com", true);

        DailyBranchCloseReport report = dailyCloseService.generateFinal(
                UUID.fromString(businessId), UUID.fromString(branchId), LocalDate.now(ZoneOffset.UTC));

        UUID legacyRowId = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        "INSERT INTO owner_notification_log "
                                + "(id, daily_close_report_id, business_id, branch_id, business_contact_id, "
                                + "recipient_email, channel, status, triggered_by, attempted_at) "
                                + "VALUES (:id, :reportId, :businessId, :branchId, :contactId, "
                                + "'legacy@example.com', 'EMAIL', 'SENT', 'AUTO', now())")
                .setParameter("id", legacyRowId)
                .setParameter("reportId", report.getId())
                .setParameter("businessId", UUID.fromString(businessId))
                .setParameter("branchId", UUID.fromString(branchId))
                .setParameter("contactId", UUID.fromString(contactId))
                .executeUpdate();

        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT report_type, report_period FROM owner_notification_log WHERE id = :id")
                .setParameter("id", legacyRowId)
                .getSingleResult();
        assertThat(row[0]).isEqualTo("DAILY");
        assertThat(row[1]).isNull();
    }

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

    private List<OwnerNotificationLog> findMonthlyLogs(String branchId, YearMonth periodMonth) {
        List<?> ids = entityManager
                .createNativeQuery(
                        "SELECT id FROM owner_notification_log WHERE report_type = 'MONTHLY' AND branch_id = :branchId "
                                + "AND report_period = :period ORDER BY attempted_at ASC")
                .setParameter("branchId", UUID.fromString(branchId))
                .setParameter("period", periodMonth.atDay(1))
                .getResultList();
        return ids.stream().map(id -> logRepository.findById((UUID) id).orElseThrow()).toList();
    }

    private void pushAttemptedAtBack(UUID logId, Instant newAttemptedAt) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> entityManager
                        .createNativeQuery("UPDATE owner_notification_log SET attempted_at = :ts WHERE id = :id")
                        .setParameter("ts", newAttemptedAt)
                        .setParameter("id", logId)
                        .executeUpdate());
    }

    /**
     * Filters the shared GreenMail mailbox to messages addressed to a specific recipient - the
     * mailbox isn't test-isolated on its own (AbstractIntegrationTest's Postgres container, and
     * therefore every branch/contact ever created in the suite, is shared across all test
     * classes), so raw {@code GREEN_MAIL.getReceivedMessages()} counts can pick up unrelated
     * leftover sends.
     */
    private List<MimeMessage> messagesTo(String email) throws Exception {
        List<MimeMessage> matches = new ArrayList<>();
        for (MimeMessage message : GREEN_MAIL.getReceivedMessages()) {
            for (jakarta.mail.Address recipient : message.getAllRecipients()) {
                if (recipient.toString().equalsIgnoreCase(email)) {
                    matches.add(message);
                    break;
                }
            }
        }
        return matches;
    }

    private void waitForMessagesTo(String email, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (messagesTo(email).size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(messagesTo(email)).hasSize(expected);
    }

    private void setBranchTimezone(String branchId, String staffCookie, String timezone) throws Exception {
        mockMvc.perform(post("/api/staff/branches/{branchId}/timezone", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"" + timezone + "\"}"))
                .andExpect(status().isOk());
    }

    private String createContact(String businessId, String staffCookie, String email, boolean monthlyReportRecipient)
            throws Exception {
        String body = "{\"name\":\"Sahip\",\"phone\":null,\"email\":\"" + email + "\",\"whatsappEnabled\":false,"
                + "\"dailyReportRecipient\":false,\"monthlyReportRecipient\":" + monthlyReportRecipient + "}";
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
