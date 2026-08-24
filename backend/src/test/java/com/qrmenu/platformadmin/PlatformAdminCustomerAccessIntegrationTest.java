package com.qrmenu.platformadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A business/branch deactivated by the platform admin (distinct from Branch.orderingEnabled,
 * the branch's own temporary-closed toggle) must reject new customer check-in, new cart
 * items, and new payments with 503 - never a generic error - while an already-established
 * order's own tracking/receipt access keeps working regardless (Section: platform admin
 * deactivation, product ask #2). Covers both the branch- and business-level flag, since
 * TenantService.assertBusinessAndBranchActive rejects on either being inactive.
 */
class PlatformAdminCustomerAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void deactivatedBranchRejectsNewCheckIn() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Checkin Block Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String qrToken = createQrTokenForNewTable(businessId, branchId, "Masa 1");
        deactivateBranchAsPlatformAdmin(businessId, branchId, "pa-checkin-block");

        mockMvc.perform(post("/api/qr/{token}/visit", qrToken)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void deactivatedBusinessAlsoRejectsNewCheckIn() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Checkin Block Business (biz)");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String qrToken = createQrTokenForNewTable(businessId, branchId, "Masa 1");
        deactivateBusinessAsPlatformAdmin(businessId, "pa-checkin-block-biz");

        mockMvc.perform(post("/api/qr/{token}/visit", qrToken)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void deactivatedBranchRejectsNewCartItemsForAnAlreadyCheckedInVisit() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cart Block Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", 2000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        String qrToken = createQrTokenForNewTable(businessId, branchId, "Masa 1");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        deactivateBranchAsPlatformAdmin(businessId, branchId, "pa-cart-block");

        mockMvc.perform(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void deactivatedBranchRejectsPaymentStartForAnAlreadyDraftedCart() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Payment Block Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", 2000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        String qrToken = createQrTokenForNewTable(businessId, branchId, "Masa 1");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        mockMvc.perform(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated());

        // DRAFT is not an "active order" (OrderingService.ACTIVE_ORDER_STATUSES), so
        // deactivation is allowed to proceed even with this cart pending.
        deactivateBranchAsPlatformAdmin(businessId, branchId, "pa-payment-block");

        mockMvc.perform(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue())))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void existingOrderTrackingAndReceiptStillWorkAfterTheBranchIsDeactivated() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Tracking Survives Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cashierCookie = bootstrapCashier(businessId, branchId, "tracking-survives-cashier");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", 2000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        String qrToken = createQrTokenForNewTable(businessId, branchId, "Masa 1");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        MvcResult cartResult = mockMvc.perform(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        String trackingToken = objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("orderTrackingToken").asText();

        MvcResult intentResult = mockMvc.perform(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue())))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = objectMapper.readTree(intentResult.getResponse().getContentAsString()).get("paymentId").asText();
        mockMvc.perform(post("/api/table-visits/{tableVisitId}/payments/{paymentId}/mock-outcome", visit.tableVisitId(), paymentId)
                        .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isAccepted());

        String orderId = pollForPendingAcceptanceOrderId(branchId, cashierCookie);
        // Terminal (REJECTED_BY_STORE), so the branch no longer has an active order and
        // can actually be deactivated below - AWAITING_STORE_ACCEPTANCE itself would be
        // rejected by the same active-order guard PlatformAdminBranchManagementIntegrationTest covers.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchId, orderId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isOk());

        deactivateBranchAsPlatformAdmin(businessId, branchId, "pa-tracking-survives");

        JsonNode tracking = objectMapper.readTree(mockMvc.perform(get("/api/order-tracking/{token}", trackingToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(tracking.get("status").asText()).isEqualTo("REJECTED_BY_STORE");

        mockMvc.perform(get("/api/order-tracking/{token}/receipt", trackingToken)).andExpect(status().isOk());
    }

    private String createQrTokenForNewTable(String businessId, String branchId, String tableLabel) throws Exception {
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, tableLabel);
        return TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
    }

    private void deactivateBranchAsPlatformAdmin(String businessId, String branchId, String platformAdminLocalPart) throws Exception {
        String homeBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, platformAdminLocalPart + "-home");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, homeBusinessId, platformAdminLocalPart + "@example.com"));
        mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/branches/{branchId}/deactivate", businessId, branchId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    private void deactivateBusinessAsPlatformAdmin(String businessId, String platformAdminLocalPart) throws Exception {
        String homeBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, platformAdminLocalPart + "-home");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, homeBusinessId, platformAdminLocalPart + "@example.com"));
        mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/deactivate", businessId).cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    /** CASHIER isn't BUSINESS_ADMIN/PLATFORM_ADMIN, so it needs an explicit branch assignment. */
    private String bootstrapCashier(String businessId, String branchId, String emailLocalPart) throws Exception {
        String email = emailLocalPart + "@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        return StaffFixtures.login(mockMvc, email);
    }

    private String pollForPendingAcceptanceOrderId(String branchId, String cashierCookie) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode pending = objectMapper.readTree(mockMvc.perform(
                            get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchId)
                                    .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            if (pending.size() > 0) {
                return pending.get(0).get("orderId").asText();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for a pending-acceptance order");
    }
}
