package com.qrmenu.platformadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.audit.AuditLogEntry;
import com.qrmenu.audit.repository.AuditLogEntryRepository;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform admin panel's remaining branch-management gaps: edit/activate/deactivate a
 * branch (never a hard delete), rejecting deactivation while an order is still
 * operationally active so it can't be stranded mid-flight, the create/edit/toggle audit
 * trail, and the deactivate-vs-new-order/payment race - see
 * PlatformAdminBranchService's Javadoc for why the branch row lock makes the two paths
 * serialize instead of racing.
 */
class PlatformAdminBranchManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    @Test
    void platformAdminCanEditActivateAndDeactivateABranch() throws Exception {
        String homeBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Branch Mgmt Home");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, homeBusinessId, "pa-branch-edit@example.com"));
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, homeBusinessId, "Eski İsim");

        mockMvc.perform(put("/api/platform-admin/businesses/{businessId}/branches/{branchId}", homeBusinessId, branchId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Yeni İsim\",\"address\":\"Yeni Adres 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Yeni İsim"))
                .andExpect(jsonPath("$.address").value("Yeni Adres 1"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/branches/{branchId}/deactivate",
                                homeBusinessId,
                                branchId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/branches/{branchId}/activate",
                                homeBusinessId,
                                branchId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void platformAdminCannotDeactivateABranchWithAnOrderStillAwaitingPayment() throws Exception {
        String homeBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Active Order Home");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, homeBusinessId, "pa-active-order@example.com"));
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Active Order Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String qrToken = createQrTokenForNewTable(businessId, branchId, "Masa 1");
        createDraftOrderAndBeginPayment(businessId, branchId, qrToken);

        mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/branches/{branchId}/deactivate", businessId, branchId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/platform-admin/businesses/{businessId}/branches/{branchId}", businessId, branchId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Şube\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void creatingEditingAndTogglingWritesAuditEntries() throws Exception {
        String homeBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Audit Home");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, homeBusinessId, "pa-audit@example.com"));

        JsonNode businessBody = readBody(mockMvc.perform(post("/api/platform-admin/businesses")
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Audited Business\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        String businessId = businessBody.get("id").asText();

        JsonNode branchBody = readBody(mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/branches", businessId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Audited Branch\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        String branchId = branchBody.get("id").asText();

        JsonNode staffBody = readBody(mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/staff-users", businessId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"audited-staff@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn());
        String staffUserId = staffBody.get("id").asText();

        mockMvc.perform(put("/api/platform-admin/businesses/{businessId}/branches/{branchId}", businessId, branchId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed Branch\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/branches/{branchId}/deactivate", businessId, branchId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/branches/{branchId}/activate", businessId, branchId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isOk());

        List<AuditLogEntry> entries = auditLogEntryRepository.findAll().stream()
                .filter(entry -> entry.getBusinessId().equals(UUID.fromString(businessId)))
                .toList();

        assertThat(entries)
                .anySatisfy(entry -> assertAction(entry, "Business", businessId, "CREATED"))
                .anySatisfy(entry -> assertAction(entry, "Branch", branchId, "CREATED"))
                .anySatisfy(entry -> assertAction(entry, "StaffUser", staffUserId, "CREATED"))
                .anySatisfy(entry -> assertAction(entry, "Branch", branchId, "INFO_UPDATED"))
                .anySatisfy(entry -> assertAction(entry, "Branch", branchId, "DEACTIVATED"))
                .anySatisfy(entry -> assertAction(entry, "Branch", branchId, "ACTIVATED"));
    }

    /**
     * The race the plan called out: a platform admin deactivating a branch must never
     * overlap with a new payment reaching AWAITING_PAYMENT for it. Both requests share
     * the same branch-row lock (BranchRepository.findByIdAndBusinessIdForUpdate), so
     * whichever transaction acquires it first determines the (consistent) outcome for
     * the other - either the branch is deactivated and the payment attempt then sees it
     * as unavailable (503), or the payment starts first and the deactivate attempt then
     * sees an active order (409). The two outcomes below are the only valid ones; a run
     * where both "succeed" (branch inactive AND order reached AWAITING_PAYMENT) would be
     * exactly the stranded-order bug this lock exists to prevent.
     */
    @Test
    void concurrentDeactivateAndPaymentStartNeverBothSucceed() throws Exception {
        String homeBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Race Home");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, homeBusinessId, "pa-race@example.com"));
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Race Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String qrToken = createQrTokenForNewTable(businessId, branchId, "Masa 1");
        CheckedInVisit visit = createDraftOrderOnly(businessId, branchId, qrToken);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Integer> deactivateFuture = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post(
                                        "/api/platform-admin/businesses/{businessId}/branches/{branchId}/deactivate",
                                        businessId,
                                        branchId)
                                .cookie(platformAdminCookie))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            Future<Integer> paymentFuture = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId())
                                .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue())))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            startLatch.countDown();
            int deactivateStatus = deactivateFuture.get(15, TimeUnit.SECONDS);
            int paymentStatus = paymentFuture.get(15, TimeUnit.SECONDS);

            if (deactivateStatus == 200) {
                // Deactivate won the lock first (or the payment attempt lost the race
                // entirely) - the branch is gone from service, so payment start must
                // see it as unavailable, never succeed.
                assertThat(paymentStatus).isEqualTo(503);
            } else {
                // Payment won the lock first and reached AWAITING_PAYMENT - deactivate
                // must then see an active order and refuse, never silently proceed.
                assertThat(deactivateStatus).isEqualTo(409);
                assertThat(paymentStatus).isEqualTo(201);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertAction(AuditLogEntry entry, String entityType, String entityId, String action) {
        assertThat(entry.getEntityType()).isEqualTo(entityType);
        assertThat(entry.getEntityId()).isEqualTo(UUID.fromString(entityId));
        assertThat(entry.getAction()).isEqualTo(action);
    }

    private String createQrTokenForNewTable(String businessId, String branchId, String tableLabel) throws Exception {
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, tableLabel);
        return TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
    }

    /** Checks in, adds one cart item, and starts payment - order ends AWAITING_PAYMENT (an active order). */
    private void createDraftOrderAndBeginPayment(String businessId, String branchId, String qrToken) throws Exception {
        CheckedInVisit visit = createDraftOrderOnly(businessId, branchId, qrToken);
        mockMvc.perform(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue())))
                .andExpect(status().isCreated());
    }

    /** Checks in and adds one cart item - order stays DRAFT (not yet an active order). */
    private CheckedInVisit createDraftOrderOnly(String businessId, String branchId, String qrToken) throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", 2000);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        mockMvc.perform(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated());
        return visit;
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
