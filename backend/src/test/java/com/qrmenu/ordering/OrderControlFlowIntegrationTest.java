package com.qrmenu.ordering;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.payment.MockPaymentProviderAdapter;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @MockitoSpyBean
    private MockPaymentProviderAdapter paymentProvider;

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
        // Bölüm 19.3 kasa kartı: masa etiketi ve "ne kadar süredir bekliyor" zaman damgası.
        assertThat(pending.get(0).get("tableLabel").asText()).isEqualTo("Masa 1");
        assertThat(pending.get(0).get("statusSince").asText()).isNotBlank();
        // Section 6/27: branch'in configurable kasa kabul timeout'u - varsayılan 5 dakika.
        assertThat(pending.get(0).get("storeAcceptanceTimeoutSeconds").asInt()).isEqualTo(300);

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId).cookie(cashierCookie(cashierCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_KITCHEN"));

        // No longer pending - and unlike the removed KITCHEN_STAFF-only KDS, CASHIER
        // holds Permission.ORDER_PREPARE too, so it sees the order in the in-progress
        // queue (Section 6/11: kasa siparişin tamamı için tek operasyon ekranı).
        assertThat(objectMapper.readTree(mockMvc.perform(pendingGet(branchId, cashierCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()))
                .isEmpty();
        JsonNode inProgress = objectMapper.readTree(mockMvc.perform(
                        get("/api/staff/branches/{branchId}/orders/in-progress", branchId).cookie(cashierCookie(cashierCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(inProgress).hasSize(1);
        assertThat(inProgress.get(0).get("orderId").asText()).isEqualTo(orderId);
    }

    @Test
    void pendingAcceptanceReflectsTheBranchsConfiguredTimeout() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Timeout Order Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "timeout-admin@example.com");
        mockMvc.perform(post("/api/staff/branches/{branchId}/store-acceptance-timeout", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSeconds\":600}"))
                .andExpect(status().isOk());

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Kahve", 3000, 1);

        JsonNode pending = objectMapper.readTree(mockMvc.perform(pendingGet(branchId, adminCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(pending.get(0).get("storeAcceptanceTimeoutSeconds").asInt()).isEqualTo(600);
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

    /**
     * Two REJECT clicks fired at (as close as a test can get to) the same instant -
     * e.g. a cashier double-tapping, or two staff devices both open on the same
     * pending order. OrderingService.rejectOrder now reads the order row with the same
     * FOR UPDATE lock RefundService.requestFullRefund already used, so the second
     * request has to wait for the first's whole reject+refund sequence to finish
     * before it even re-reads the order status - it then fails immediately and
     * cleanly (400, "already rejected"), never getting far enough to attempt a second
     * refund. Money-safety itself (never refunding more than was paid) is also
     * independently guaranteed one layer down by Payment.applyRefund's own row lock
     * (see RefundFlowIntegrationTest's concurrent-refund coverage) - this test proves
     * the ordering-level race is closed too, not just the payment-level invariant.
     */
    @Test
    void concurrentDoubleRejectIssuesExactlyOneFullRefundAndTheSecondAttemptFailsCleanly() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Concurrent Reject Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cashierCookie = bootstrapCashier(businessId, branchId, "cashier-reject-concurrent");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Poğaça", 1500, 2);
        String trackingToken = lastTrackingToken;

        String orderId = objectMapper.readTree(mockMvc.perform(pendingGet(branchId, cashierCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0)
                .get("orderId")
                .asText();

        String rejectBody = "{\"reasonCode\":\"OUT_OF_STOCK\"}";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> performConcurrentReject(start, branchId, orderId, rejectBody, cashierCookie));
            Future<Integer> second = executor.submit(() -> performConcurrentReject(start, branchId, orderId, rejectBody, cashierCookie));
            start.countDown();

            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        } finally {
            executor.shutdownNow();
        }

        // Exactly one refund, fully completed, for exactly the paid amount (3000) -
        // never double-refunded regardless of which request "won".
        JsonNode receipt = objectMapper.readTree(mockMvc.perform(get("/api/order-tracking/{token}/receipt", trackingToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(receipt.get("totalRefundedMinorUnits").asLong()).isEqualTo(3000);
        assertThat(receipt.get("netPaidMinorUnits").asLong()).isEqualTo(0);
        assertThat(receipt.get("refunds")).hasSize(1);
        assertThat(receipt.get("refunds").get(0).get("status").asText()).isEqualTo("COMPLETED");
    }

    /**
     * Section 6: "Refund başarısız olursa ... sipariş sessizce 'iptal edildi'
     * sayılmaz" - a provider-side refund failure must not roll back or hide the
     * rejection itself. The order stays REJECTED_BY_STORE (never silently reverted to
     * AWAITING_STORE_ACCEPTANCE or masked as CANCELLED), the refund is recorded as its
     * own FAILED row rather than dropped, the payment's refunded total stays exactly 0
     * (the failed attempt released its reservation), and both the customer's tracking
     * view and the staff order-history/latestRefundStatus surface FAILED - never
     * COMPLETED - so nobody is misled into thinking the money already moved.
     */
    @Test
    void refundProviderFailureOnRejectKeepsTheOrderRejectedWithAFailedRefundStatus() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Refund Failure Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cashierCookie = bootstrapCashier(businessId, branchId, "cashier-reject-failure");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Ayran", 1000, 4);
        String trackingToken = lastTrackingToken;

        String orderId = objectMapper.readTree(mockMvc.perform(pendingGet(branchId, cashierCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0)
                .get("orderId")
                .asText();

        doThrow(new IllegalStateException("Provider rejected refund"))
                .when(paymentProvider)
                .refund(anyString(), eq(4000L));

        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchId, orderId)
                        .cookie(cashierCookie(cashierCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED_BY_STORE"));

        JsonNode tracking = objectMapper.readTree(mockMvc.perform(get("/api/order-tracking/{token}", trackingToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(tracking.get("status").asText()).isEqualTo("REJECTED_BY_STORE");
        assertThat(tracking.get("latestRefundStatus").asText()).isEqualTo("FAILED");

        JsonNode receipt = objectMapper.readTree(mockMvc.perform(get("/api/order-tracking/{token}/receipt", trackingToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(receipt.get("totalRefundedMinorUnits").asLong()).isZero();
        assertThat(receipt.get("netPaidMinorUnits").asLong()).isEqualTo(4000);
        assertThat(receipt.get("refunds").get(0).get("status").asText()).isEqualTo("FAILED");

        // The branch has no configured timezone, so getOrderHistory resolves its
        // calendar-day range via TenantService.resolveBranchTimeZone, which falls back
        // to the business's defaultTimeZone ("Europe/Istanbul" - see TenantFixtures.
        // createBusiness / Business's single-arg constructor), never a bare UTC guess.
        // Matching that here keeps this assertion correct regardless of what the local
        // wall-clock date happens to be relative to Europe/Istanbul's.
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
        JsonNode history = objectMapper.readTree(mockMvc.perform(get(
                                "/api/staff/branches/{branchId}/orders/history?from=" + today + "&to=" + today, branchId)
                        .cookie(cashierCookie(cashierCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("orderId").asText()).isEqualTo(orderId);
        assertThat(history.get(0).get("status").asText()).isEqualTo("REJECTED_BY_STORE");
        assertThat(history.get(0).get("latestRefundStatus").asText()).isEqualTo("FAILED");
    }

    private int performConcurrentReject(CountDownLatch start, String branchId, String orderId, String body, String staffCookie)
            throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchId, orderId)
                        .cookie(cashierCookie(staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String lastTrackingToken;

    /** CASHIER isn't BUSINESS_ADMIN/PLATFORM_ADMIN, so it needs an explicit branch assignment like BRANCH_MANAGER. */
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
