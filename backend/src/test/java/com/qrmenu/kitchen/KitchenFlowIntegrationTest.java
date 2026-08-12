package com.qrmenu.kitchen;

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

import java.util.UUID;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Milestone 6 (kitchen queue mechanics) and its Milestone 8 auth retrofit: an
 * order the cashier has ACCEPTed (gap-analysis #1 - payment success alone no longer
 * queues the kitchen, see OrderControlController) lands in the KDS queue with a
 * readable order number, partial accept/reject per item, the IN_KITCHEN -> READY
 * rollup once every item is decided/ready, and that kitchen endpoints require a real
 * staff session (qrmenu_staff_session cookie, resolved through StaffAuthService)
 * rather than the Milestone 6 shared-secret guard.
 */
class KitchenFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aPaidOrderReachesTheKitchenQueueWithAnOrderNumberAndCanBeFullyAcceptedThroughToReady() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Kitchen Business");
        String staffCookie = bootstrapStaffAdmin(businessId, "kitchen-admin-1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createPaidOrderWithOneItem(businessId, branchId, qrToken, "Çorba", 5000, 2, staffCookie);

        JsonNode queue = objectMapper.readTree(mockMvc.perform(kitchenGet(branchId, "/orders", staffCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(queue).hasSize(1);
        JsonNode order = queue.get(0);
        assertThat(order.get("status").asText()).isEqualTo("IN_KITCHEN");
        assertThat(order.get("orderNumber").asInt()).isGreaterThanOrEqualTo(1);
        // Bölüm 19.3 KDS kartı: masa etiketi ve "sipariş yaşı" zaman damgası.
        assertThat(order.get("tableLabel").asText()).isEqualTo("Masa 1");
        assertThat(order.get("statusSince").asText()).isNotBlank();
        String orderItemId = order.get("items").get(0).get("id").asText();
        assertThat(order.get("items").get(0).get("status").asText()).isEqualTo("PENDING_REVIEW");

        mockMvc.perform(kitchenPost(branchId, "/order-items/" + orderItemId + "/decide", "{\"acceptedQuantity\":2}", staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_KITCHEN"))
                .andExpect(jsonPath("$.items[0].status").value("PREPARING"))
                .andExpect(jsonPath("$.items[0].acceptedQuantity").value(2))
                .andExpect(jsonPath("$.items[0].rejectedQuantity").value(0));

        mockMvc.perform(kitchenPost(branchId, "/order-items/" + orderItemId + "/ready", null, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.items[0].status").value("READY"));

        mockMvc.perform(kitchenPost(branchId, "/order-items/" + orderItemId + "/served", null, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.items[0].status").value("SERVED"));

        // A served/ready order is no longer in the active IN_KITCHEN queue.
        JsonNode queueAfter = objectMapper.readTree(mockMvc.perform(kitchenGet(branchId, "/orders", staffCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(queueAfter).isEmpty();
    }

    @Test
    void aFullyRejectedSingleItemOrderStillRollsUpToReady() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Reject Business");
        String staffCookie = bootstrapStaffAdmin(businessId, "kitchen-admin-2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createPaidOrderWithOneItem(businessId, branchId, qrToken, "Tatlı", 3000, 1, staffCookie);

        JsonNode queue = objectMapper.readTree(mockMvc.perform(kitchenGet(branchId, "/orders", staffCookie))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String orderItemId = queue.get(0).get("items").get(0).get("id").asText();

        mockMvc.perform(kitchenPost(branchId, "/order-items/" + orderItemId + "/decide", "{\"acceptedQuantity\":0}", staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].rejectedQuantity").value(1));
    }

    @Test
    void orderNumbersAreSequentialPerBranchPerDay() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Sequence Business");
        String staffCookie = bootstrapStaffAdmin(businessId, "kitchen-admin-3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableAId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa A");
        String tableBId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa B");
        String qrTokenA = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableAId);
        String qrTokenB = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableBId);

        createPaidOrderWithOneItem(businessId, branchId, qrTokenA, "Ürün A", 1000, 1, staffCookie);
        createPaidOrderWithOneItem(businessId, branchId, qrTokenB, "Ürün B", 1000, 1, staffCookie);

        JsonNode queue = objectMapper.readTree(mockMvc.perform(kitchenGet(branchId, "/orders", staffCookie))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(queue).hasSize(2);
        assertThat(queue.get(1).get("orderNumber").asInt()).isEqualTo(queue.get(0).get("orderNumber").asInt() + 1);
    }

    @Test
    void kitchenEndpointsRejectMissingOrInvalidStaffSession() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Guard Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");

        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders", branchId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theOrderTrackingEndpointReflectsKitchenProgressAndRejectsAnUnknownToken() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Tracking Business");
        String staffCookie = bootstrapStaffAdmin(businessId, "kitchen-admin-4");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);

        MvcResult cartResult = addOneItemToCart(businessId, branchId, qrToken, "Su", 1000, 1);
        JsonNode cartBody = objectMapper.readTree(cartResult.getResponse().getContentAsString());
        String trackingToken = cartBody.get("orderTrackingToken").asText();
        CheckedInVisit visit = lastVisit;
        payDraftOrder(visit, staffCookie);

        mockMvc.perform(get("/api/order-tracking/{token}", trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_KITCHEN"))
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.items[0].productName").value("Su"));

        mockMvc.perform(get("/api/order-tracking/{token}", "not-a-real-token")).andExpect(status().isNotFound());
    }

    private CheckedInVisit lastVisit;

    private String bootstrapStaffAdmin(String businessId, String emailLocalPart) throws Exception {
        return StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, emailLocalPart + "@example.com");
    }

    private CheckedInVisit createPaidOrderWithOneItem(
            String businessId, String branchId, String qrToken, String productName, long priceMinorUnits, int quantity, String staffCookie)
            throws Exception {
        addOneItemToCart(businessId, branchId, qrToken, productName, priceMinorUnits, quantity);
        payDraftOrder(lastVisit, staffCookie);
        return lastVisit;
    }

    private MvcResult addOneItemToCart(
            String businessId, String branchId, String qrToken, String productName, long priceMinorUnits, int quantity)
            throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, productName, priceMinorUnits, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        lastVisit = visit;
        return mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private void payDraftOrder(CheckedInVisit visit, String staffCookie) throws Exception {
        MvcResult intentResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId()), visit))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = objectMapper.readTree(intentResult.getResponse().getContentAsString()).get("paymentId").asText();

        mockMvc.perform(withCookie(
                        post("/api/table-visits/{tableVisitId}/payments/{paymentId}/mock-outcome", visit.tableVisitId(), paymentId), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isAccepted());

        String orderId = pollUntilOrderStatus(visit, paymentId, "AWAITING_STORE_ACCEPTANCE");

        // Gap-analysis #1: payment success no longer auto-queues the kitchen - the
        // cashier has to accept first (Permission.ORDER_ACCEPT).
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", visit.branchId(), orderId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_KITCHEN"));
    }

    private String pollUntilOrderStatus(CheckedInVisit visit, String paymentId, String expectedOrderStatus) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult statusResult = mockMvc.perform(withCookie(
                            get("/api/table-visits/{tableVisitId}/payments/{paymentId}", visit.tableVisitId(), paymentId), visit))
                    .andExpect(status().isOk())
                    .andReturn();
            last = objectMapper.readTree(statusResult.getResponse().getContentAsString());
            if (expectedOrderStatus.equals(last.get("orderStatus").asText())) {
                return last.get("orderId").asText();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for orderStatus=" + expectedOrderStatus + ", last=" + last);
    }

    private MockHttpServletRequestBuilder kitchenGet(String branchId, String path, String staffCookie) {
        return get("/api/kitchen/branches/{branchId}" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
    }

    private MockHttpServletRequestBuilder kitchenPost(String branchId, String path, String jsonBody, String staffCookie) {
        MockHttpServletRequestBuilder request = post("/api/kitchen/branches/{branchId}" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
        return jsonBody == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
