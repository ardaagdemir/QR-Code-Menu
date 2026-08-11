package com.qrmenu.ordering;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #1 (product-requirements.md Section 6): the cashier acceptance gate
 * itself - OrderControlController's pending-acceptance/accept/reject endpoints, the
 * CASHIER role/Permission.ORDER_* boundaries, and that REJECT triggers an automatic
 * full refund without ever touching the kitchen queue.
 */
class OrderControlFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aPaidOrderWaitsForCashierAcceptanceThenReachesTheKitchenQueue() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "OrderControl Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cashierCookie = bootstrapCashier(businessId, branchId, "cashier-accept-1");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Kahve", 3000, 1);

        JsonNode pending = objectMapper.readTree(mockMvc.perform(pendingGet(branchId, cashierCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(pending).hasSize(1);
        String orderId = pending.get(0).get("orderId").asText();
        assertThat(pending.get(0).get("status").asText()).isEqualTo("AWAITING_STORE_ACCEPTANCE");

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId).cookie(cashierCookie(cashierCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_KITCHEN"));

        // No longer pending, and CASHIER (no Permission.KITCHEN_DECIDE) can't see the
        // KDS queue itself - only that the order left AWAITING_STORE_ACCEPTANCE behind.
        assertThat(objectMapper.readTree(mockMvc.perform(pendingGet(branchId, cashierCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()))
                .isEmpty();
        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders", branchId).cookie(cashierCookie(cashierCookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void kitchenStaffCannotAcceptOrRejectOrders() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Permission Boundary Business");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "boundary-admin@example.com");
        String kitchenStaffEmail = "boundary-kitchen@example.com";
        mockMvc.perform(post("/api/staff/staff-users")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + kitchenStaffEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"KITCHEN_STAFF\"}"))
                .andExpect(status().isCreated());
        String kitchenCookie = StaffFixtures.login(mockMvc, kitchenStaffEmail);

        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Çay", 1500, 1);

        String orderId = objectMapper.readTree(mockMvc.perform(pendingGet(branchId, adminCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0)
                .get("orderId")
                .asText();

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId).cookie(cashierCookie(kitchenCookie)))
                .andExpect(status().isForbidden());
        mockMvc.perform(pendingGet(branchId, kitchenCookie)).andExpect(status().isForbidden());
    }

    @Test
    void storeRejectionRefundsTheFullPaidAmountAndNeverReachesTheKitchen() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Reject Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cashierCookie = bootstrapCashier(businessId, branchId, "cashier-reject-1");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Simit", 2000, 3);

        String orderId = objectMapper.readTree(mockMvc.perform(pendingGet(branchId, cashierCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0)
                .get("orderId")
                .asText();

        JsonNode rejected = objectMapper.readTree(mockMvc.perform(
                        post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchId, orderId)
                                .cookie(cashierCookie(cashierCookie))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reasonCode\":\"OUT_OF_STOCK\",\"note\":\"Simit bitti\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED_BY_STORE"))
                .andExpect(jsonPath("$.rejectionReasonCode").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.rejectionNote").value("Simit bitti"))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED_BY_STORE");

        // Never reaches the kitchen, never reappears in the pending queue.
        assertThat(objectMapper.readTree(mockMvc.perform(pendingGet(branchId, cashierCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()))
                .isEmpty();

        // A full refund (3 * 2000 = 6000) was issued automatically.
        String trackingToken = lastTrackingToken;
        JsonNode receipt = objectMapper.readTree(mockMvc.perform(get("/api/order-tracking/{token}/receipt", trackingToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(receipt.get("totalRefundedMinorUnits").asLong()).isEqualTo(6000);
        assertThat(receipt.get("netPaidMinorUnits").asLong()).isEqualTo(0);

        // Gap-analysis #6: the customer's own tracking view reflects the refund too.
        JsonNode tracking = objectMapper.readTree(mockMvc.perform(get("/api/order-tracking/{token}", trackingToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(tracking.get("status").asText()).isEqualTo("REJECTED_BY_STORE");
        assertThat(tracking.get("latestRefundStatus").asText()).isEqualTo("COMPLETED");

        // Already terminal - a second reject attempt is rejected.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchId, orderId)
                        .cookie(cashierCookie(cashierCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isBadRequest());
    }

    private String lastTrackingToken;

    /** CASHIER isn't BUSINESS_ADMIN/PLATFORM_ADMIN, so it needs an explicit branch assignment like BRANCH_MANAGER/KITCHEN_STAFF. */
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

    private CheckedInVisit payAndAwaitStoreAcceptance(
            String businessId, String branchId, String qrToken, String productName, long priceMinorUnits, int quantity) throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, productName, priceMinorUnits, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        MvcResult cartResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        lastTrackingToken = objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("orderTrackingToken").asText();

        MvcResult intentResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId()), visit))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = objectMapper.readTree(intentResult.getResponse().getContentAsString()).get("paymentId").asText();
        mockMvc.perform(withCookie(
                        post("/api/table-visits/{tableVisitId}/payments/{paymentId}/mock-outcome", visit.tableVisitId(), paymentId), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isAccepted());

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult statusResult = mockMvc.perform(withCookie(
                            get("/api/table-visits/{tableVisitId}/payments/{paymentId}", visit.tableVisitId(), paymentId), visit))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode statusBody = objectMapper.readTree(statusResult.getResponse().getContentAsString());
            if ("AWAITING_STORE_ACCEPTANCE".equals(statusBody.get("orderStatus").asText())) {
                return visit;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for order to reach AWAITING_STORE_ACCEPTANCE");
    }

    private MockHttpServletRequestBuilder pendingGet(String branchId, String staffCookie) {
        return get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchId).cookie(cashierCookie(staffCookie));
    }

    private MockCookie cashierCookie(String staffCookie) {
        return new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
