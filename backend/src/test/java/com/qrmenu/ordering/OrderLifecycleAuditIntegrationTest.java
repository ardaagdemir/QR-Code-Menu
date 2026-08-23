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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end audit of the paid-order lifecycle: PAYMENT SUCCESS -> AWAITING_STORE_ACCEPTANCE
 * -> IN_KITCHEN -> READY -> COMPLETED. Complements the narrower existing coverage
 * (OrderControlFlowIntegrationTest, OrderPreparationFlowIntegrationTest) with: SSE delivery
 * on both the customer-tracking and Kasa channels at every transition, a stream opened after
 * the fact ("reconnect") always reflecting the current backend state rather than something
 * stale, sequential duplicate/invalid transitions rejected cleanly, and branch isolation on
 * every mutating order-control endpoint.
 */
class OrderLifecycleAuditIntegrationTest extends AbstractIntegrationTest {

    /**
     * The single broadest assertion in this class: every hop of the happy path pushes an
     * "order-status" SSE event carrying the right orderId/orderStatus/orderNumber to BOTH a
     * customer tracking subscriber (subscribed once, up front) and the Kasa branch-kitchen
     * subscriber - and a stream opened only after the fact ("reconnect", e.g. a phone that
     * was locked/backgrounded and just came back) sees no stale backlog itself (Section 2:
     * "initial connect delivers no backlog") but the REST reload right after reconnecting
     * always reports the true, current status - never something left over from before.
     */
    @Test
    void paidOrderLifecycleEmitsSseAtEveryTransitionAndReconnectAlwaysSeesCurrentState() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Lifecycle SSE Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "lifecycle-sse-admin@example.com");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);

        CartAndPayment cartAndPayment = addItemAndPay(businessId, branchId, qrToken, "Kahve", 3000, 1);
        String trackingToken = cartAndPayment.trackingToken;
        CheckedInVisit visit = cartAndPayment.visit;
        String paymentId = cartAndPayment.paymentId;
        String orderId = pollUntilOrderStatus(visit, paymentId, "AWAITING_STORE_ACCEPTANCE");

        // Subscribed up front, before any of the three transitions below.
        MvcResult customerStream = mockMvc.perform(get("/api/order-tracking/{token}/stream", trackingToken))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult kasaStream = mockMvc.perform(get("/api/staff/branches/{branchId}/orders/stream", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_KITCHEN"));
        assertThat(lastOrderStatusEvent(customerStream)).isEqualTo("IN_KITCHEN");
        assertThat(lastOrderStatusEvent(kasaStream)).isEqualTo("IN_KITCHEN");
        // A subscriber that only connects now ("reconnect") gets no replayed backlog on the
        // stream itself - but the very next plain reload already reports the true state.
        assertThat(reconnectAndReadCurrentStatusFromTracking(trackingToken)).isEqualTo("IN_KITCHEN");

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/ready", branchId, orderId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        assertThat(lastOrderStatusEvent(customerStream)).isEqualTo("READY");
        assertThat(lastOrderStatusEvent(kasaStream)).isEqualTo("READY");
        assertThat(reconnectAndReadCurrentStatusFromTracking(trackingToken)).isEqualTo("READY");

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchId, orderId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk());
        assertThat(lastOrderStatusEvent(customerStream)).isEqualTo("COMPLETED");
        assertThat(lastOrderStatusEvent(kasaStream)).isEqualTo("COMPLETED");
        assertThat(reconnectAndReadCurrentStatusFromTracking(trackingToken)).isEqualTo("COMPLETED");

        // A COMPLETED order is off every active Kasa column...
        assertThat(objectMapper.readTree(mockMvc.perform(staffGet(branchId, "/pending-acceptance", staffCookie))
                        .andReturn().getResponse().getContentAsString())).isEmpty();
        assertThat(objectMapper.readTree(mockMvc.perform(staffGet(branchId, "/in-progress", staffCookie))
                        .andReturn().getResponse().getContentAsString())).isEmpty();
        assertThat(objectMapper.readTree(mockMvc.perform(staffGet(branchId, "/ready", staffCookie))
                        .andReturn().getResponse().getContentAsString())).isEmpty();
        // ...but still reachable in history, keyed by the same readable order number the
        // customer/refund views already showed - never the raw internal UUID.
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
        JsonNode history = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/history?from=" + today + "&to=" + today, staffCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("orderId").asText()).isEqualTo(orderId);
        assertThat(history.get(0).get("status").asText()).isEqualTo("COMPLETED");
        Integer orderNumber = history.get(0).get("orderNumber").asInt();

        JsonNode tracking = objectMapper.readTree(mockMvc.perform(get("/api/order-tracking/{token}", trackingToken))
                .andReturn().getResponse().getContentAsString());
        assertThat(tracking.get("orderNumber").asInt()).isEqualTo(orderNumber);
    }

    /** Gap-analysis #1: no webhook, no payment success -> the order stays out of every Kasa column, forever a DRAFT. */
    @Test
    void anUnpaidOrderNeverReachesTheKasaAcceptanceQueue() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Unpaid Gate Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "unpaid-admin@example.com");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Su", 1000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        MvcResult cartResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        String draftOrderId = objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("orderId").asText();

        assertThat(objectMapper.readTree(mockMvc.perform(staffGet(branchId, "/pending-acceptance", staffCookie))
                        .andReturn().getResponse().getContentAsString())).isEmpty();

        // Not even a direct ACCEPT against the still-DRAFT order id is honored - the store
        // acceptance gate only opens once the order has actually moved past a real payment.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, draftOrderId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isBadRequest());

        // Payment created but not yet resolved (still PROCESSING) - still no Kasa entry.
        mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId()), visit))
                .andExpect(status().isCreated());
        assertThat(objectMapper.readTree(mockMvc.perform(staffGet(branchId, "/pending-acceptance", staffCookie))
                        .andReturn().getResponse().getContentAsString())).isEmpty();
    }

    /**
     * Section 6's whole point: every transition method (CustomerOrder.markInKitchen/
     * markReady/markCompleted) guards its own current-status precondition and throws
     * IllegalStateException - mapped to 400 by ApiExceptionHandler - so a second, sequential
     * ACCEPT/READY/COMPLETE against an order that already moved past that step is rejected
     * outright rather than silently re-applied or corrupting already-advanced state.
     */
    @Test
    void duplicateSequentialAcceptReadyAndCompleteRequestsAreRejectedWithoutChangingState() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Duplicate Action Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "duplicate-admin@example.com");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CartAndPayment cartAndPayment = addItemAndPay(businessId, branchId, qrToken, "Poğaça", 1500, 1);
        String orderId = pollUntilOrderStatus(cartAndPayment.visit, cartAndPayment.paymentId, "AWAITING_STORE_ACCEPTANCE");

        MockCookie staff = new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId).cookie(staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_KITCHEN"));
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId).cookie(staff))
                .andExpect(status().isBadRequest());
        // A REJECT after acceptance makes no more sense than a second ACCEPT - also rejected.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchId, orderId)
                        .cookie(staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/ready", branchId, orderId).cookie(staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/ready", branchId, orderId).cookie(staff))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchId, orderId).cookie(staff))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchId, orderId).cookie(staff))
                .andExpect(status().isBadRequest());

        // Exactly one COMPLETED order, not duplicated/corrupted by any of the rejected retries.
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
        JsonNode history = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/history?from=" + today + "&to=" + today, staffCookie))
                .andReturn().getResponse().getContentAsString());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("status").asText()).isEqualTo("COMPLETED");
    }

    /** Out-of-order actions (skip a step) are rejected the same way as duplicates of a past step. */
    @Test
    void outOfOrderTransitionsAreRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Out Of Order Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "outoforder-admin@example.com");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CartAndPayment cartAndPayment = addItemAndPay(businessId, branchId, qrToken, "Ayran", 1000, 1);
        String orderId = pollUntilOrderStatus(cartAndPayment.visit, cartAndPayment.paymentId, "AWAITING_STORE_ACCEPTANCE");
        MockCookie staff = new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);

        // READY before ACCEPT.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/ready", branchId, orderId).cookie(staff))
                .andExpect(status().isBadRequest());
        // COMPLETE before ACCEPT/READY.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchId, orderId).cookie(staff))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId).cookie(staff))
                .andExpect(status().isOk());

        // COMPLETE before READY.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchId, orderId).cookie(staff))
                .andExpect(status().isBadRequest());

        // The order is still exactly where the one legal transition left it.
        JsonNode inProgress = objectMapper.readTree(mockMvc.perform(staffGet(branchId, "/in-progress", staffCookie))
                .andReturn().getResponse().getContentAsString());
        assertThat(inProgress).hasSize(1);
        assertThat(inProgress.get(0).get("status").asText()).isEqualTo("IN_KITCHEN");
    }

    /**
     * Every mutating order-control endpoint scopes its lookup to the caller's own branchId
     * (OrderingService.requireOrderInBranch/getOrderInBranchForUpdate) - an orderId that's
     * real, just owned by a different branch of a different business, must 404 exactly like
     * an unknown id would, never leak a cross-tenant IllegalStateException or succeed.
     */
    @Test
    void crossBranchOrderActionsAreRejectedAsNotFound() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cross Branch Business");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");
        String staffA = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchA, "cross-branch-a-admin@example.com");
        String staffB = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchB, "cross-branch-b-admin@example.com");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchA, "Masa A1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CartAndPayment cartAndPayment = addItemAndPay(businessId, branchA, qrToken, "Tost", 2500, 1);
        String orderId = pollUntilOrderStatus(cartAndPayment.visit, cartAndPayment.paymentId, "AWAITING_STORE_ACCEPTANCE");

        MockCookie staffBCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, staffB);

        // Branch B's staff cannot see branch A's order in its own pending list...
        assertThat(objectMapper.readTree(mockMvc.perform(staffGet(branchB, "/pending-acceptance", staffB))
                        .andReturn().getResponse().getContentAsString())).isEmpty();
        // ...and every mutating action against it from branch B 404s, not 200/500.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchB, orderId).cookie(staffBCookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchB, orderId)
                        .cookie(staffBCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/ready", branchB, orderId).cookie(staffBCookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchB, orderId).cookie(staffBCookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/refunds", branchB, orderId)
                        .cookie(staffBCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"orderItemId\":\"" + java.util.UUID.randomUUID() + "\",\"quantity\":1}]}"))
                .andExpect(status().isNotFound());

        // Branch A's own staff can still act on it normally - isolation, not just breakage.
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchA, orderId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_KITCHEN"));
    }

    private record CartAndPayment(CheckedInVisit visit, String trackingToken, String paymentId) {
    }

    private CartAndPayment addItemAndPay(
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
        String trackingToken = objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("orderTrackingToken").asText();

        MvcResult intentResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId()), visit))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = objectMapper.readTree(intentResult.getResponse().getContentAsString()).get("paymentId").asText();
        mockMvc.perform(withCookie(
                        post("/api/table-visits/{tableVisitId}/payments/{paymentId}/mock-outcome", visit.tableVisitId(), paymentId), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isAccepted());
        return new CartAndPayment(visit, trackingToken, paymentId);
    }

    /** Simulates a reconnecting client: a brand-new stream subscription plus a fresh plain reload, same as page.tsx's visibilitychange handler does. */
    private String reconnectAndReadCurrentStatusFromTracking(String trackingToken) throws Exception {
        mockMvc.perform(get("/api/order-tracking/{token}/stream", trackingToken)).andExpect(request().asyncStarted());
        return objectMapper
                .readTree(mockMvc.perform(get("/api/order-tracking/{token}", trackingToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("status")
                .asText();
    }

    /** Parses the most recent "order-status" SSE event's data payload out of an in-flight stream's accumulated response body. */
    private String lastOrderStatusEvent(MvcResult streamResult) throws Exception {
        String body = streamResult.getResponse().getContentAsString();
        String[] lines = body.split("\n");
        String lastDataLine = null;
        for (String line : lines) {
            if (line.startsWith("data:")) {
                lastDataLine = line.substring("data:".length());
            }
        }
        assertThat(lastDataLine).as("no order-status event observed on stream; body was: " + body).isNotNull();
        return objectMapper.readTree(lastDataLine).get("orderStatus").asText();
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

    private MockHttpServletRequestBuilder staffGet(String branchId, String path, String staffCookie) {
        return get("/api/staff/branches/{branchId}/orders" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
