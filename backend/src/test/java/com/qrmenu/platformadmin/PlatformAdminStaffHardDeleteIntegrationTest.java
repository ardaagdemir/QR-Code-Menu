package com.qrmenu.platformadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.audit.AuditLogEntry;
import com.qrmenu.audit.repository.AuditLogEntryRepository;
import com.qrmenu.expense.Expense;
import com.qrmenu.expense.repository.ExpenseRepository;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.staffaccess.StaffUser;
import com.qrmenu.staffaccess.repository.StaffSessionRepository;
import com.qrmenu.staffaccess.repository.StaffUserBranchRepository;
import com.qrmenu.staffaccess.repository.StaffUserRepository;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DELETE /api/platform-admin/businesses/{businessId}/staff-users/{staffUserId} - covers the
 * approved hard-delete design: irreversible removal of the StaffUser row itself (unlike
 * deactivate, which only flips active=false), with supporting FKs falling back to
 * ON DELETE SET NULL (V33) so audit/expense history survives the delete, distinguishably
 * from genuine system-initiated entries via AuditLogEntry.actorAccountDeleted.
 */
class PlatformAdminStaffHardDeleteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private StaffUserBranchRepository staffUserBranchRepository;

    @Autowired
    private StaffSessionRepository staffSessionRepository;

    @Autowired
    private StaffAuthService staffAuthService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void platformAdminCanHardDeleteAStaffUserInAnotherBusinessAndItDisappearsFromListAndLogin() throws Exception {
        String homeBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Hard Delete Home Business");
        String otherBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Hard Delete Other Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, otherBusinessId, "Şube");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, homeBusinessId, "hd-actor@example.com"));

        String targetEmail = "hd-target-cross-business@example.com";
        String targetId = objectMapper
                .readTree(mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/staff-users", otherBusinessId)
                                .cookie(platformAdminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + targetEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(delete(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}",
                                otherBusinessId,
                                targetId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/platform-admin/businesses/{businessId}/staff-users", otherBusinessId).cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + targetId + "')]").doesNotExist());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + targetEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformAdminCannotHardDeleteAnotherPlatformAdminAccount() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Hard Delete Platform Admin Target Business");
        MockCookie actingPlatformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "hd-actor-2@example.com"));
        String targetPlatformAdminId = objectMapper
                .readTree(mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                                .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"hd-target-platform-admin@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"PLATFORM_ADMIN\",\"branchIds\":[]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(delete(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}",
                                businessId,
                                targetPlatformAdminId)
                        .cookie(actingPlatformAdminCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform-admin/businesses/{businessId}/staff-users", businessId).cookie(actingPlatformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + targetPlatformAdminId + "')].role").value("PLATFORM_ADMIN"));

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hd-target-platform-admin@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    /** target.id == actor.id always implies target.role == PLATFORM_ADMIN (only PLATFORM_ADMIN can
     * reach this endpoint), so this is rejected by the same PLATFORM_ADMIN-target guard as the test
     * above - covered here explicitly since "cannot delete your own account" is the property that
     * actually matters to a caller, regardless of which guard happens to catch it first. */
    @Test
    void platformAdminCannotHardDeleteItsOwnAccount() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Hard Delete Self Business");
        String email = "hd-self@example.com";
        MockCookie platformAdminCookie =
                new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, email));

        String ownStaffUserId = objectMapper
                .readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(platformAdminCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("staffUserId")
                .asText();

        mockMvc.perform(delete("/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}", businessId, ownStaffUserId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void hardDeletingAStaffUserInvalidatesItsSessionsAndAnonymizesItsAuditAndExpenseHistory() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Hard Delete History Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "hd-history-actor@example.com"));

        String targetEmail = "hd-history-target@example.com";
        MockCookie targetCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, targetEmail));
        String targetId = objectMapper
                .readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(targetCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("staffUserId")
                .asText();

        // Target authors expense history (created_by) and audit entries while it's still alive.
        String categoryId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/expense-categories")
                                .cookie(targetCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Kira\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
        String expenseId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/expenses")
                                .cookie(targetCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                        + "\",\"amountMinorUnits\":1000,\"incurredAt\":\"2026-08-01\",\"vendor\":\"Ev Sahibi\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(delete("/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}", businessId, targetId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isNoContent());

        // Session invalidation: the target's own still-cookied session is dead.
        mockMvc.perform(get("/api/staff/auth/me").cookie(targetCookie)).andExpect(status().isUnauthorized());

        // Expense history survives with creator nulled out by the FK's ON DELETE SET NULL.
        Expense expense = expenseRepository.findById(UUID.fromString(expenseId)).orElseThrow();
        assertThat(expense.getCreatedByStaffUserId()).isNull();

        // Audit history survives, actor anonymized and flagged - distinguishable from a genuine system actor.
        // Scoped to this test's own entity ids - the shared Testcontainers Postgres instance is reused
        // across the whole test run (see AbstractIntegrationTest), so other classes' Expense/ExpenseCategory
        // audit rows are present too and must not leak into this assertion.
        List<UUID> ownEntityIds = List.of(UUID.fromString(expenseId), UUID.fromString(categoryId));
        List<AuditLogEntry> targetAuthoredEntries =
                auditLogEntryRepository.findAll().stream().filter(entry -> ownEntityIds.contains(entry.getEntityId())).toList();
        assertThat(targetAuthoredEntries).hasSize(2); // ExpenseCategory CREATED, Expense CREATED
        assertThat(targetAuthoredEntries)
                .allSatisfy(entry -> {
                    assertThat(entry.getActorStaffUserId()).isNull();
                    assertThat(entry.isActorAccountDeleted()).isTrue();
                });
    }

    /**
     * StaffAuthService.hardDeleteStaffUserAsPlatformAdmin locks the target row
     * (StaffUserRepository.findByIdAndBusinessIdForUpdate, PESSIMISTIC_WRITE) for the whole
     * transaction specifically so a concurrent login() for the same user can never complete
     * its session insert in the gap between the session cleanup step and the actual DELETE -
     * see that repository method's Javadoc. This test drives the two sides of that race with
     * explicit latches (deterministic, not relying on incidental thread-scheduling timing):
     * one thread replays hard-delete's own steps (lock -> pause -> cleanup -> delete, using
     * the exact same repository calls the real method does), the other calls the real
     * StaffAuthService.login for that same user while the lock is held. If this regresses
     * back to an unlocked read, the login thread would complete immediately instead of
     * blocking, and this test's "still not done after the sleep" assertion would fail.
     */
    @Test
    void hardDeleteRowLockBlocksAConcurrentLoginUntilTheLockIsReleasedAndTheLoginThenFailsOnceTheUserIsGone() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Hard Delete Lock Race Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String targetEmail = "hd-lock-race-target@example.com";
        MockCookie targetCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, targetEmail, "CASHIER"));
        UUID targetId = UUID.fromString(objectMapper
                .readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(targetCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("staffUserId")
                .asText());
        UUID businessUuid = UUID.fromString(businessId);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> hardDeleteSimulation = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                StaffUser locked =
                        staffUserRepository.findByIdAndBusinessIdForUpdate(targetId, businessUuid).orElseThrow();
                lockAcquired.countDown();
                awaitUninterruptibly(releaseLock);
                staffSessionRepository.deleteAllByStaffUserId(targetId);
                staffUserBranchRepository.deleteAllByStaffUserId(targetId);
                staffUserRepository.delete(locked);
            }));

            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();

            Future<StaffAuthService.LoginResult> loginAttempt =
                    executor.submit(() -> staffAuthService.login(targetEmail, StaffFixtures.DEFAULT_PASSWORD));

            // The lock is held - the racing login must not be able to finish creating its session yet.
            Thread.sleep(500);
            assertThat(loginAttempt.isDone()).isFalse();

            releaseLock.countDown();
            hardDeleteSimulation.get(10, TimeUnit.SECONDS);

            // Unblocked once the holding transaction committed (the user is now actually gone) -
            // the login must fail, never quietly succeed with a session pointing at a deleted user.
            assertThatThrownBy(() -> loginAttempt.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);

            assertThat(staffUserRepository.findById(targetId)).isEmpty();
            assertThat(staffSessionRepository.findAll()).noneMatch(session -> session.getStaffUserId().equals(targetId));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
