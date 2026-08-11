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
import org.springframework.transaction.annotation.Transactional;

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
 * scheduled method directly rather than waiting for its real 5-minute trigger.
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

    @Test
    @Transactional
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

    private JsonNode createPaymentIntentForNewOrder(String businessId, String branchId, String qrToken, long priceMinorUnits)
            throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", priceMinorUnits, 10);
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
        entityManager
                .createNativeQuery("UPDATE payment SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, Timestamp.from(backdatedTo))
                .setParameter(2, UUID.fromString(paymentId))
                .executeUpdate();
        entityManager.clear();
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
