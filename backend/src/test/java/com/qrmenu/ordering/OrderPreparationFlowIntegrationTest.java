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

import java.util.UUID;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Product decision (no separate Mutfak/KDS screen, and no separate item-level kitchen
 * decision step): what used to be KitchenFlowIntegrationTest against a standalone
 * KitchenController - an order the cashier has ACCEPTed (gap-analysis #1) lands in the
 * Kasa "in-progress" queue with a readable order number, every item auto-accepted in
 * full (OrderItem.acceptFully - no partial accept/reject per item anymore), and moves
 * PREPARING -> READY as a single order-level action, requiring a real staff session -
 * exercised here through OrderControlController's merged in-progress/{orderId}/ready
 * endpoint (Permission.ORDER_VIEW / Permission.ORDER_PREPARE) instead of the removed
 * /api/kitchen/** surface.
 */
class OrderPreparationFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aPaidOrderReachesTheInProgressQueueWithAnOrderNumberAndItemsAutoAcceptedThroughToReady() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Preparation Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "prep-admin-1");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createPaidOrderWithOneItem(businessId, branchId, qrToken, "Çorba", 5000, 2, staffCookie);

        JsonNode queue = objectMapper.readTree(mockMvc.perform(inProgressGet(branchId, staffCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(queue).hasSize(1);
        JsonNode order = queue.get(0);
        assertThat(order.get("status").asText()).isEqualTo("IN_KITCHEN");
        assertThat(order.get("orderNumber").asInt()).isGreaterThanOrEqualTo(1);
        // Bölüm 19.3 kasa kartı: masa etiketi ve "sipariş yaşı" zaman damgası.
        assertThat(order.get("tableLabel").asText()).isEqualTo("Masa 1");
        assertThat(order.get("statusSince").asText()).isNotBlank();
        String orderId = order.get("orderId").asText();
        // Product decision: ACCEPT already auto-accepted the full ordered quantity - no PENDING_REVIEW/decide step left.
        assertThat(order.get("items").get(0).get("status").asText()).isEqualTo("PREPARING");
        assertThat(order.get("items").get(0).get("acceptedQuantity").asInt()).isEqualTo(2);
        assertThat(order.get("items").get(0).get("rejectedQuantity").asInt()).isEqualTo(0);

        mockMvc.perform(orderPost(branchId, "/" + orderId + "/ready", staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.items[0].status").value("READY"));

        // A READY order is no longer in the active in-progress queue.
        JsonNode queueAfter = objectMapper.readTree(mockMvc.perform(inProgressGet(branchId, staffCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(queueAfter).isEmpty();
    }

    /**
     * Product decision: Kasa'nın "Hazır - Teslim Bekliyor" bölümü (Section 6/8) - bir
     * sipariş PREPARING'den READY'ye geçtiğinde in-progress kuyruğundan düşer ama
     * /orders/ready'de görünmeye devam eder, ta ki personel ORDER_COMPLETE ile teslim
     * işaretleyene kadar (bkz. RefundController.completeOrder, aynı davranış - ayrı bir
     * URL taşıması gerekmedi).
     */
    @Test
    void aReadyOrderAppearsInTheReadyListUntilCompleted() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Ready Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "prep-admin-5");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        createPaidOrderWithOneItem(businessId, branchId, qrToken, "Limonata", 2500, 1, staffCookie);

        JsonNode queue = objectMapper.readTree(mockMvc.perform(inProgressGet(branchId, staffCookie))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String orderId = queue.get(0).get("orderId").asText();

        mockMvc.perform(orderPost(branchId, "/" + orderId + "/ready", staffCookie)).andExpect(status().isOk());

        JsonNode readyList = objectMapper.readTree(mockMvc.perform(
                        get("/api/staff/branches/{branchId}/orders/ready", branchId)
                                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(readyList).hasSize(1);
        assertThat(readyList.get(0).get("orderId").asText()).isEqualTo(orderId);

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchId, orderId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk());

        JsonNode readyListAfter = objectMapper.readTree(mockMvc.perform(
                        get("/api/staff/branches/{branchId}/orders/ready", branchId)
                                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(readyListAfter).isEmpty();
    }

    @Test
    void orderNumbersAreSequentialPerBranchPerDay() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Sequence Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "prep-admin-3");
        String tableAId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa A");
        String tableBId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa B");
        String qrTokenA = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableAId);
        String qrTokenB = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableBId);

        createPaidOrderWithOneItem(businessId, branchId, qrTokenA, "Ürün A", 1000, 1, staffCookie);
        createPaidOrderWithOneItem(businessId, branchId, qrTokenB, "Ürün B", 1000, 1, staffCookie);

        JsonNode queue = objectMapper.readTree(mockMvc.perform(inProgressGet(branchId, staffCookie))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(queue).hasSize(2);
        assertThat(queue.get(1).get("orderNumber").asInt()).isEqualTo(queue.get(0).get("orderNumber").asInt() + 1);
    }

    @Test
    void inProgressEndpointRejectsMissingOrInvalidStaffSession() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Guard Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");

        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/in-progress", branchId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/in-progress", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theOrderTrackingEndpointReflectsPreparationProgressAndRejectsAnUnknownToken() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Tracking Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "prep-admin-4");
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

    private String bootstrapStaffAdmin(String businessId, String branchId, String emailLocalPart) throws Exception {
        return StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, emailLocalPart + "@example.com");
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

        // Gap-analysis #1: payment success no longer auto-queues preparation - the
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

    private MockHttpServletRequestBuilder inProgressGet(String branchId, String staffCookie) {
        return get("/api/staff/branches/{branchId}/orders/in-progress", branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
    }

    private MockHttpServletRequestBuilder orderPost(String branchId, String path, String staffCookie) {
        return post("/api/staff/branches/{branchId}/orders" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
