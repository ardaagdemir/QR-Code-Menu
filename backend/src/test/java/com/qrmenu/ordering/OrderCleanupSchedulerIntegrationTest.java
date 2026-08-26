package com.qrmenu.ordering;

import com.qrmenu.ordering.repository.OrderRepository;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the DRAFT-cart TTL cleanup job (Section 7: "Terk edilmiş DRAFT sepetlerin
 * otomatik temizlenmesi (2 saat hareketsizlik sonrası CANCELLED)"). Backdates
 * last_activity_at directly via SQL (no production code exists to do this - a real
 * DRAFT only ever goes stale by the passage of real time) and invokes the scheduled
 * method directly rather than waiting for its real 15-minute trigger.
 */
class OrderCleanupSchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderCleanupScheduler scheduler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void staleDraftOrdersAreCancelledButFreshOnesAreLeftAlone() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cleanup Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");

        String staleTableId =
                TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Stale Masa");
        String staleQrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, staleTableId);
        String staleOrderId = createDraftOrderViaCart(businessId, branchId, staleQrToken, "Stale Ürün");
        backdateLastActivity(staleOrderId, Instant.now().minus(OrderCleanupScheduler.DRAFT_TTL).minusSeconds(60));

        String freshTableId =
                TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Fresh Masa");
        String freshQrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, freshTableId);
        String freshOrderId = createDraftOrderViaCart(businessId, branchId, freshQrToken, "Fresh Ürün");

        scheduler.cancelStaleDraftOrders();

        assertThat(orderRepository.findById(java.util.UUID.fromString(staleOrderId)))
                .isPresent()
                .get()
                .extracting(CustomerOrder::getStatus)
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderRepository.findById(java.util.UUID.fromString(freshOrderId)))
                .isPresent()
                .get()
                .extracting(CustomerOrder::getStatus)
                .isEqualTo(OrderStatus.DRAFT);
    }

    private String createDraftOrderViaCart(String businessId, String branchId, String qrToken, String productName)
            throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, productName, 1000);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        MvcResult result = mockMvc.perform(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText();
    }

    private void backdateLastActivity(String orderId, Instant backdatedTo) {
        entityManager
                .createNativeQuery("UPDATE customer_order SET last_activity_at = ?1 WHERE id = ?2")
                .setParameter(1, Timestamp.from(backdatedTo))
                .setParameter(2, java.util.UUID.fromString(orderId))
                .executeUpdate();
        entityManager.clear();
    }
}
