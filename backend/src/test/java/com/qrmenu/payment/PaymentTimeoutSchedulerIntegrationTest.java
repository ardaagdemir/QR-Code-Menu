package com.qrmenu.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.OrderStatus;
import com.qrmenu.ordering.repository.OrderRepository;
import com.qrmenu.payment.repository.PaymentRepository;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Milestone 9's "hata senaryoları: webhook timeout" - a Payment stuck in
 * PROCESSING because no webhook (real or mock) ever arrives eventually expires,
 * freeing its Order to be retried, instead of blocking that table visit forever.
 * Backdates Payment.createdAt directly via SQL (no production code path does this - a
 * real payment only ever goes stale by the passage of real time) and invokes the
 * scheduled method directly rather than waiting for its real 5-minute trigger. Not
 * @Transactional at the test-method level (unlike most of this suite): PaymentTimeoutExpirer
 * uses REQUIRES_NEW per payment so each one commits/rolls back for real - a shared,
 * still-open test transaction would hide that from these assertions (its own writes
 * would look committed to a same-transaction read even while rollback-only) and would
 * also make its own uncommitted fixture rows invisible to that suspended REQUIRES_NEW
 * transaction. Mirrors PaymentFlowIntegrationTest's convention for the same reason.
 */
class PaymentTimeoutSchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentTimeoutScheduler scheduler;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void staleProcessingPaymentsExpireAndTheirOrdersBecomeRetriableButFreshOnesAreLeftAlone() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Timeout Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");

        String staleTableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Stale Masa");
        String staleQrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, staleTableId);
        JsonNode staleIntent = createPaymentIntentForNewOrder(businessId, branchId, staleQrToken, 4000);
        String stalePaymentId = staleIntent.get("paymentId").asText();
        String staleOrderId = staleIntent.get("orderId").asText();
        backdatePaymentCreatedAt(stalePaymentId, Instant.now().minus(PaymentTimeoutScheduler.PROCESSING_TIMEOUT).minusSeconds(60));

        String freshTableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Fresh Masa");
        String freshQrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, freshTableId);
        JsonNode freshIntent = createPaymentIntentForNewOrder(businessId, branchId, freshQrToken, 4000);
        String freshPaymentId = freshIntent.get("paymentId").asText();
        String freshOrderId = freshIntent.get("orderId").asText();

        scheduler.expireStaleProcessingPayments();

        assertThat(paymentRepository.findById(UUID.fromString(stalePaymentId)))
                .isPresent()
                .get()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(orderRepository.findById(UUID.fromString(staleOrderId)))
                .isPresent()
                .get()
                .extracting(CustomerOrder::getStatus)
                .isEqualTo(OrderStatus.PAYMENT_FAILED);

        assertThat(paymentRepository.findById(UUID.fromString(freshPaymentId)))
                .isPresent()
                .get()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.PROCESSING);
        assertThat(orderRepository.findById(UUID.fromString(freshOrderId)))
                .isPresent()
                .get()
                .extracting(CustomerOrder::getStatus)
                .isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    /**
     * Production-readiness: a single corrupted record must not block other records or
     * roll back the whole batch. Simulates an order whose status drifted away from
     * AWAITING_PAYMENT through some other path while its payment was still PROCESSING
     * (markOrderPaymentFailed's IllegalStateException guard, CustomerOrder.java:117) -
     * that one payment must stay untouched (its markExpired()+markOrderPaymentFailed()
     * is atomic - no half-applied EXPIRED-but-order-not-failed state), while a normal
     * stale payment elsewhere in the same poll cycle still expires correctly.
     */
    @Test
    void oneStalePaymentWithAnInconsistentOrderDoesNotBlockOrRollBackOtherPayments() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Timeout Isolation Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");

        String brokenTableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Broken Masa");
        String brokenQrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, brokenTableId);
        JsonNode brokenIntent = createPaymentIntentForNewOrder(businessId, branchId, brokenQrToken, 4000);
        String brokenPaymentId = brokenIntent.get("paymentId").asText();
        String brokenOrderId = brokenIntent.get("orderId").asText();
        backdatePaymentCreatedAt(brokenPaymentId, Instant.now().minus(PaymentTimeoutScheduler.PROCESSING_TIMEOUT).minusSeconds(60));
        corruptOrderStatus(brokenOrderId, "CANCELLED");

        String goodTableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Good Masa");
        String goodQrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, goodTableId);
        JsonNode goodIntent = createPaymentIntentForNewOrder(businessId, branchId, goodQrToken, 4000);
        String goodPaymentId = goodIntent.get("paymentId").asText();
        String goodOrderId = goodIntent.get("orderId").asText();
        backdatePaymentCreatedAt(goodPaymentId, Instant.now().minus(PaymentTimeoutScheduler.PROCESSING_TIMEOUT).minusSeconds(60));

        scheduler.expireStaleProcessingPayments();

        assertThat(paymentRepository.findById(UUID.fromString(brokenPaymentId)))
                .isPresent()
                .get()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.PROCESSING);
        assertThat(orderRepository.findById(UUID.fromString(brokenOrderId)))
                .isPresent()
                .get()
                .extracting(CustomerOrder::getStatus)
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(paymentRepository.findById(UUID.fromString(goodPaymentId)))
                .isPresent()
                .get()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(orderRepository.findById(UUID.fromString(goodOrderId)))
                .isPresent()
                .get()
                .extracting(CustomerOrder::getStatus)
                .isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    private void corruptOrderStatus(String orderId, String status) {
        runInTransaction(() -> entityManager
                .createNativeQuery("UPDATE customer_order SET status = ?1 WHERE id = ?2")
                .setParameter(1, status)
                .setParameter(2, UUID.fromString(orderId))
                .executeUpdate());
    }

    private JsonNode createPaymentIntentForNewOrder(String businessId, String branchId, String qrToken, long priceMinorUnits)
            throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", priceMinorUnits);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated());
        MvcResult intentResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId()), visit))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(intentResult.getResponse().getContentAsString());
    }

    private void backdatePaymentCreatedAt(String paymentId, Instant backdatedTo) {
        runInTransaction(() -> entityManager
                .createNativeQuery("UPDATE payment SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, Timestamp.from(backdatedTo))
                .setParameter(2, UUID.fromString(paymentId))
                .executeUpdate());
    }

    /** Runs a single native update in its own real transaction, since the test method itself isn't @Transactional. */
    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            action.run();
            entityManager.clear();
        });
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
