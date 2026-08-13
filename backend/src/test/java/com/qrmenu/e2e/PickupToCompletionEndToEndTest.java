package com.qrmenu.e2e;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 9 (+ gap-analysis #1's cashier gate): one true end-to-end test walking the
 * full CUSTOMER_PICKUP path that every other integration test only covers in isolated
 * slices - QR check-in -> cart -> mock payment -> cashier ACCEPT (auto-accepts every
 * item, no separate kitchen decision step) -> cashier marks the order READY -> the
 * order appearing on the branch's public pickup board -> staff marking it complete ->
 * the order leaving the pickup board and the customer's own tracking view reflecting
 * COMPLETED + the CUSTOMER_PICKUP delivery model throughout.
 */
class PickupToCompletionEndToEndTest extends AbstractIntegrationTest {

    @Test
    void aCustomerPickupOrderFlowsFromQrScanThroughPickupBoardToCompletion() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "E2E Pickup Business");
        String branchId =
                TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube", "CUSTOMER_PICKUP");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "e2e-admin@example.com");
        MockCookie staffMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Kahve", 3000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        // QR scan -> check-in -> add to cart.
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        MvcResult cartResult = mockMvc.perform(withCustomerCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        String trackingToken =
                objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("orderTrackingToken").asText();

        // Empty pickup board before anything is READY.
        assertThat(objectMapper.readTree(mockMvc.perform(get("/api/branches/{branchId}/pickup-board", branchId))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()))
                .isEmpty();

        // Mock payment: CREATED -> PROCESSING -> (async webhook) -> SUCCEEDED, order -> IN_KITCHEN.
        MvcResult intentResult = mockMvc.perform(withCustomerCookie(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId()), visit))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = objectMapper.readTree(intentResult.getResponse().getContentAsString()).get("paymentId").asText();
        mockMvc.perform(withCustomerCookie(
                        post("/api/table-visits/{tableVisitId}/payments/{paymentId}/mock-outcome", visit.tableVisitId(), paymentId), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isAccepted());
        String orderId = pollUntilOrderStatus(visit, paymentId, "AWAITING_STORE_ACCEPTANCE");

        // Gap-analysis #1: the cashier must ACCEPT before the order reaches the kitchen.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId).cookie(staffMockCookie))
                .andExpect(status().isOk());

        // The in-progress queue shows the item already auto-accepted in full. Cashier marks the whole order ready.
        JsonNode queue = objectMapper.readTree(mockMvc.perform(
                        get("/api/staff/branches/{branchId}/orders/in-progress", branchId).cookie(staffMockCookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).get("items").get(0).get("status").asText()).isEqualTo("PREPARING");
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/ready", branchId, orderId).cookie(staffMockCookie))
                .andExpect(status().isOk());

        // The order now shows up on the public pickup board.
        JsonNode board = objectMapper.readTree(mockMvc.perform(get("/api/branches/{branchId}/pickup-board", branchId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(board).hasSize(1);
        assertThat(board.get(0).get("orderId").asText()).isEqualTo(orderId);

        // The customer's own tracking view agrees: READY, CUSTOMER_PICKUP.
        JsonNode tracking = objectMapper.readTree(mockMvc.perform(get("/api/order-tracking/{token}", trackingToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(tracking.get("status").asText()).isEqualTo("READY");
        assertThat(tracking.get("deliveryModel").asText()).isEqualTo("CUSTOMER_PICKUP");

        // Staff marks it collected.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchId, orderId).cookie(staffMockCookie))
                .andExpect(status().isOk());

        // Gone from the pickup board, COMPLETED in tracking, and a second completion attempt is rejected.
        assertThat(objectMapper.readTree(mockMvc.perform(get("/api/branches/{branchId}/pickup-board", branchId))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()))
                .isEmpty();
        JsonNode finalTracking = objectMapper.readTree(mockMvc.perform(get("/api/order-tracking/{token}", trackingToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(finalTracking.get("status").asText()).isEqualTo("COMPLETED");
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchId, orderId).cookie(staffMockCookie))
                .andExpect(status().isBadRequest());
    }

    private String pollUntilOrderStatus(CheckedInVisit visit, String paymentId, String expectedOrderStatus) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(withCustomerCookie(
                            get("/api/table-visits/{tableVisitId}/payments/{paymentId}", visit.tableVisitId(), paymentId), visit))
                    .andExpect(status().isOk())
                    .andReturn();
            last = objectMapper.readTree(result.getResponse().getContentAsString());
            if (expectedOrderStatus.equals(last.get("orderStatus").asText())) {
                return last.get("orderId").asText();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for orderStatus=" + expectedOrderStatus + ", last=" + last);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCustomerCookie(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
