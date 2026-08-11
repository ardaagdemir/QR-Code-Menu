package com.qrmenu.refund;

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
 * Covers Milestone 7 (refund/receipt mechanics) and its Milestone 8 auth retrofit:
 * staff-initiated full/partial refund, the "toplam iade tutarı ödenen tutarı aşamaz"
 * invariant (Section 1.3) across multiple partial refunds, order lookup by readable
 * order number, the printable receipt endpoint, and that refund endpoints require a
 * real staff session rather than the Milestone 6 shared-secret guard.
 */
class RefundFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void staffCanIssueAPartialRefundForARejectedItemFoundByOrderNumber() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Refund Business");
        String staffCookie = bootstrapStaffAdmin(businessId, "refund-admin-1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createPaidOrderWithOneItem(businessId, branchId, qrToken, "Pizza", 10000, 3);

        JsonNode order = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/search?orderNumber=" + fetchLatestOrderNumber(branchId, staffCookie), staffCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        String orderId = order.get("orderId").asText();
        String orderItemId = order.get("items").get(0).get("id").asText();
        assertThat(order.get("totalMinorUnits").asLong()).isEqualTo(30000);

        // Kitchen rejects 1 of the 3 (e.g. out of stock) - staff now manually refunds that unit.
        mockMvc.perform(kitchenPost(branchId, "/order-items/" + orderItemId + "/decide", "{\"acceptedQuantity\":2}", staffCookie))
                .andExpect(status().isOk());

        MvcResult refundResult = mockMvc.perform(staffPost(
                                branchId,
                                "/" + orderId + "/refunds",
                                "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}",
                                staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalAmountMinorUnits").value(10000))
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(1))
                .andReturn();
        String refundId = objectMapper.readTree(refundResult.getResponse().getContentAsString()).get("refundId").asText();

        mockMvc.perform(staffGet(branchId, "/" + orderId + "/refunds", staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].refundId").value(refundId))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void multiplePartialRefundsAccumulateAndCannotExceedThePaidAmount() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Over Refund Business");
        String staffCookie = bootstrapStaffAdmin(businessId, "refund-admin-2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createPaidOrderWithOneItem(businessId, branchId, qrToken, "Tatlı", 5000, 4);

        JsonNode order = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/search?orderNumber=" + fetchLatestOrderNumber(branchId, staffCookie), staffCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        String orderId = order.get("orderId").asText();
        String orderItemId = order.get("items").get(0).get("id").asText();
        // Total paid: 4 * 5000 = 20000.

        // First partial refund: 2 units = 10000. Leaves exactly 10000 refundable.
        mockMvc.perform(staffPost(
                        branchId, "/" + orderId + "/refunds", "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":2}]}",
                        staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountMinorUnits").value(10000));

        // Second partial refund: another 2 units = 10000. Exactly exhausts the remaining balance - must succeed.
        mockMvc.perform(staffPost(
                        branchId, "/" + orderId + "/refunds", "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":2}]}",
                        staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountMinorUnits").value(10000));

        mockMvc.perform(staffGet(branchId, "/" + orderId + "/refunds", staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        // A third refund of any positive amount must now be rejected - the full 20000 paid is already refunded.
        mockMvc.perform(staffPost(
                        branchId, "/" + orderId + "/refunds", "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}",
                        staffCookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theReceiptEndpointReflectsRefundsAndTheNetPaidAmount() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Receipt Business");
        String staffCookie = bootstrapStaffAdmin(businessId, "refund-admin-3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);

        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Çorba", 4000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        MvcResult cartResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();
        String trackingToken =
                objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("orderTrackingToken").asText();
        payDraftOrder(visit);

        JsonNode order = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/search?orderNumber=" + fetchLatestOrderNumber(branchId, staffCookie), staffCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        String orderId = order.get("orderId").asText();
        String orderItemId = order.get("items").get(0).get("id").asText();

        mockMvc.perform(staffPost(
                        branchId, "/" + orderId + "/refunds", "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}",
                        staffCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/order-tracking/{token}/receipt", trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Receipt Business"))
                .andExpect(jsonPath("$.totalMinorUnits").value(8000))
                .andExpect(jsonPath("$.totalRefundedMinorUnits").value(4000))
                .andExpect(jsonPath("$.netPaidMinorUnits").value(4000))
                .andExpect(jsonPath("$.refunds", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void refundEndpointsRejectMissingStaffSession() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Guard Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");

        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders/search?orderNumber=1", branchId)).andExpect(status().isUnauthorized());
    }

    private String bootstrapStaffAdmin(String businessId, String emailLocalPart) throws Exception {
        return StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, emailLocalPart + "@example.com");
    }

    private int fetchLatestOrderNumber(String branchId, String staffCookie) throws Exception {
        JsonNode queue = objectMapper.readTree(mockMvc.perform(kitchenGet(branchId, "/orders", staffCookie))
                .andReturn()
                .getResponse()
                .getContentAsString());
        if (queue.size() > 0) {
            return queue.get(queue.size() - 1).get("orderNumber").asInt();
        }
        throw new IllegalStateException("No orders currently in kitchen queue for branch " + branchId);
    }

    private CheckedInVisit createPaidOrderWithOneItem(
            String businessId, String branchId, String qrToken, String productName, long priceMinorUnits, int quantity)
            throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, productName, priceMinorUnits, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated());
        payDraftOrder(visit);
        return visit;
    }

    private void payDraftOrder(CheckedInVisit visit) throws Exception {
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
            if ("IN_KITCHEN".equals(statusBody.get("orderStatus").asText())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for order to reach IN_KITCHEN");
    }

    private MockHttpServletRequestBuilder kitchenGet(String branchId, String path, String staffCookie) {
        return get("/api/kitchen/branches/{branchId}" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
    }

    private MockHttpServletRequestBuilder kitchenPost(String branchId, String path, String jsonBody, String staffCookie) {
        return post("/api/kitchen/branches/{branchId}" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
    }

    private MockHttpServletRequestBuilder staffGet(String branchId, String path, String staffCookie) {
        return get("/api/kitchen/branches/{branchId}/orders" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
    }

    private MockHttpServletRequestBuilder staffPost(String branchId, String path, String jsonBody, String staffCookie) {
        return post("/api/kitchen/branches/{branchId}/orders" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
