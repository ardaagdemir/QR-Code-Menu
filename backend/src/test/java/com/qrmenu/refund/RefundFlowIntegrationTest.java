package com.qrmenu.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.payment.MockPaymentProviderAdapter;
import com.qrmenu.payment.PaymentService;
import com.qrmenu.payment.PaymentSummaryView;
import com.qrmenu.refund.repository.RefundItemRepository;
import com.qrmenu.refund.repository.RefundRepository;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Covers Milestone 7 (refund/receipt mechanics) and its Milestone 8 auth retrofit:
 * staff-initiated full/partial refund, the "toplam iade tutarı ödenen tutarı aşamaz"
 * invariant (Section 1.3) across multiple partial refunds, order lookup by readable
 * order number, the printable receipt endpoint, and that refund endpoints require a
 * real staff session rather than the Milestone 6 shared-secret guard.
 */
class RefundFlowIntegrationTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    private MockPaymentProviderAdapter paymentProvider;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private RefundItemRepository refundItemRepository;

    @Test
    void staffCanIssueAPartialRefundForARejectedItemFoundByOrderNumber() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Refund Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "refund-admin-1");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createPaidOrderWithOneItem(businessId, branchId, qrToken, "Pizza", 10000, 3, staffCookie);

        JsonNode order = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/search?orderNumber=" + fetchLatestOrderNumber(branchId, staffCookie), staffCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        String orderId = order.get("orderId").asText();
        String orderItemId = order.get("items").get(0).get("id").asText();
        assertThat(order.get("totalMinorUnits").asLong()).isEqualTo(30000);
        assertThat(order.get("items").get(0).get("refundedQuantity").asInt()).isZero();
        assertThat(order.get("items").get(0).get("remainingRefundableQuantity").asInt()).isEqualTo(3);

        // 1 of the 3 turns out to be unavailable after acceptance (e.g. out of stock) - staff manually refunds that unit.
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

        mockMvc.perform(staffGet(branchId, "/search?orderNumber=" + order.get("orderNumber").asInt(), staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(1))
                .andExpect(jsonPath("$.items[0].remainingRefundableQuantity").value(2));
    }

    @Test
    void fullRefundPreventsAnyFurtherRefundAndExposesZeroRemainingQuantity() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Over Refund Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "refund-admin-2");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createPaidOrderWithOneItem(businessId, branchId, qrToken, "Tatlı", 5000, 4, staffCookie);

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

        mockMvc.perform(staffGet(branchId, "/search?orderNumber=" + order.get("orderNumber").asInt(), staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(2))
                .andExpect(jsonPath("$.items[0].remainingRefundableQuantity").value(2));

        // Second partial refund: another 2 units = 10000. Exactly exhausts the remaining balance - must succeed.
        mockMvc.perform(staffPost(
                        branchId, "/" + orderId + "/refunds", "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":2}]}",
                        staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountMinorUnits").value(10000));

        mockMvc.perform(staffGet(branchId, "/" + orderId + "/refunds", staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        mockMvc.perform(staffGet(branchId, "/search?orderNumber=" + order.get("orderNumber").asInt(), staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(4))
                .andExpect(jsonPath("$.items[0].remainingRefundableQuantity").value(0));

        // A third refund of any positive amount must now be rejected - the full 20000 paid is already refunded.
        mockMvc.perform(staffPost(
                        branchId, "/" + orderId + "/refunds", "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}",
                        staffCookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateItemLinesInOneRefundAreRejectedWithoutConsumingQuantity() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Duplicate Refund Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "refund-duplicate");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        createPaidOrderWithOneItem(businessId, branchId, qrToken, "Makarna", 7000, 2, staffCookie);

        int orderNumber = fetchLatestOrderNumber(branchId, staffCookie);
        JsonNode order = readJson(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie));
        String orderId = order.get("orderId").asText();
        String orderItemId = order.get("items").get(0).get("id").asText();
        String duplicateLines = "{\"items\":["
                + "{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1},"
                + "{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}";

        mockMvc.perform(staffPost(branchId, "/" + orderId + "/refunds", duplicateLines, staffCookie))
                .andExpect(status().isBadRequest());

        mockMvc.perform(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(0))
                .andExpect(jsonPath("$.items[0].remainingRefundableQuantity").value(2))
                .andExpect(jsonPath("$.refunds", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void processingRefundIsVisibleButDoesNotConsumeRefundableQuantity() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Processing Refund Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "refund-processing");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        createPaidOrderWithOneItem(businessId, branchId, qrToken, "Kahve", 6000, 2, staffCookie);

        int orderNumber = fetchLatestOrderNumber(branchId, staffCookie);
        JsonNode order = readJson(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie));
        UUID orderId = UUID.fromString(order.get("orderId").asText());
        UUID orderItemId = UUID.fromString(order.get("items").get(0).get("id").asText());
        PaymentSummaryView payment = paymentService.getSucceededPaymentSummary(orderId);

        Refund processing = refundRepository.save(
                new Refund(UUID.fromString(businessId), orderId, payment.paymentId(), 6000));
        processing.markProcessing();
        processing = refundRepository.save(processing);
        refundItemRepository.save(new RefundItem(processing.getId(), orderItemId, 1, 6000));

        mockMvc.perform(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(0))
                .andExpect(jsonPath("$.items[0].remainingRefundableQuantity").value(2));

        mockMvc.perform(staffPost(
                        branchId,
                        "/" + orderId + "/refunds",
                        "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":2}]}",
                        staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(2))
                .andExpect(jsonPath("$.items[0].remainingRefundableQuantity").value(0));
    }

    @Test
    void failedRefundIsRecordedButDoesNotConsumeQuantityOrPaymentTotal() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Failed Refund Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "refund-failed");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        createPaidOrderWithOneItem(businessId, branchId, qrToken, "Limonata", 6000, 1, staffCookie);

        int orderNumber = fetchLatestOrderNumber(branchId, staffCookie);
        JsonNode order = readJson(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie));
        String orderId = order.get("orderId").asText();
        String orderItemId = order.get("items").get(0).get("id").asText();
        String body = "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}";

        doThrow(new IllegalStateException("Provider rejected refund"))
                .doCallRealMethod()
                .when(paymentProvider)
                .refund(anyString(), eq(6000L));

        mockMvc.perform(staffPost(branchId, "/" + orderId + "/refunds", body, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(0))
                .andExpect(jsonPath("$.items[0].remainingRefundableQuantity").value(1));
        assertThat(paymentService.getSucceededPaymentSummary(UUID.fromString(orderId)).totalRefundedAmountMinorUnits()).isZero();

        // The failed attempt released both item availability and the payment total, so retry can complete.
        mockMvc.perform(staffPost(branchId, "/" + orderId + "/refunds", body, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.refunds[0].status").value("FAILED"))
                .andExpect(jsonPath("$.refunds[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(1))
                .andExpect(jsonPath("$.items[0].remainingRefundableQuantity").value(0));
        assertThat(paymentService.getSucceededPaymentSummary(UUID.fromString(orderId)).totalRefundedAmountMinorUnits()).isEqualTo(6000);
    }

    @Test
    void concurrentDuplicateRefundsSerializeAndOnlyOneCanConsumeTheLastItemQuantity() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Concurrent Refund Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "refund-concurrent");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String inexpensiveProductId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Su", 1000);
        String expensiveProductId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ana yemek", 50000);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, inexpensiveProductId, "AVAILABLE", null);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, expensiveProductId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        addItem(visit, inexpensiveProductId, 1);
        addItem(visit, expensiveProductId, 1);
        payDraftOrder(visit, staffCookie);

        int orderNumber = fetchLatestOrderNumber(branchId, staffCookie);
        JsonNode order = readJson(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie));
        String orderId = order.get("orderId").asText();
        JsonNode inexpensiveItem = null;
        for (JsonNode item : order.get("items")) {
            if (item.get("productName").asText().equals("Su")) {
                inexpensiveItem = item;
                break;
            }
        }
        assertThat(inexpensiveItem).isNotNull();
        String orderItemId = inexpensiveItem.get("id").asText();
        String body = "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}";

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> performConcurrentRefund(start, branchId, orderId, body, staffCookie));
            Future<Integer> second = executor.submit(() -> performConcurrentRefund(start, branchId, orderId, body, staffCookie));
            start.countDown();

            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        } finally {
            executor.shutdownNow();
        }

        JsonNode refreshed = readJson(staffGet(branchId, "/search?orderNumber=" + orderNumber, staffCookie));
        JsonNode refreshedInexpensiveItem = null;
        for (JsonNode item : refreshed.get("items")) {
            if (item.get("id").asText().equals(orderItemId)) {
                refreshedInexpensiveItem = item;
                break;
            }
        }
        assertThat(refreshedInexpensiveItem).isNotNull();
        assertThat(refreshedInexpensiveItem.get("refundedQuantity").asInt()).isEqualTo(1);
        assertThat(refreshedInexpensiveItem.get("remainingRefundableQuantity").asInt()).isZero();
        assertThat(refreshed.get("refunds")).hasSize(1);
    }

    @Test
    void theReceiptEndpointReflectsRefundsAndTheNetPaidAmount() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Receipt Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = bootstrapStaffAdmin(businessId, branchId, "refund-admin-3");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);

        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Çorba", 4000);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        MvcResult cartResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();
        String trackingToken =
                objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("orderTrackingToken").asText();
        payDraftOrder(visit, staffCookie);

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

        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/search?orderNumber=1", branchId)).andExpect(status().isUnauthorized());
    }

    @Test
    void refundEndpointsCannotReachOrIssueRefundsForAnotherBranchsOrder() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Branch Isolation Business");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "A Şube");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "B Şube");
        String branchBStaffCookie = bootstrapStaffAdmin(businessId, branchB, "isolation-branch-b");
        String branchAStaffCookie = bootstrapStaffAdmin(businessId, branchA, "isolation-branch-a");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchB, "B Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        // Order lives entirely in branch B - the branch-A-scoped staff session below has no grant on branch B at all.
        createPaidOrderWithOneItem(businessId, branchB, qrToken, "Şube B Ürünü", 4000, 2, branchBStaffCookie);
        int orderNumber = fetchLatestOrderNumber(branchB, branchBStaffCookie);
        JsonNode orderInBranchB = readJson(staffGet(branchB, "/search?orderNumber=" + orderNumber, branchBStaffCookie));
        String orderId = orderInBranchB.get("orderId").asText();
        String orderItemId = orderInBranchB.get("items").get(0).get("id").asText();
        String refundBody = "{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}";

        // The branch-A staff session has no grant on branch B whatsoever - denied before any order lookup happens.
        mockMvc.perform(staffGet(branchB, "/search?orderNumber=" + orderNumber, branchAStaffCookie)).andExpect(status().isForbidden());
        mockMvc.perform(staffGet(branchB, "/" + orderId + "/refunds", branchAStaffCookie)).andExpect(status().isForbidden());
        mockMvc.perform(staffPost(branchB, "/" + orderId + "/refunds", refundBody, branchAStaffCookie)).andExpect(status().isForbidden());

        // Even addressed through branch A (which the session IS scoped to), branch B's order is invisible - 404, not leaked.
        mockMvc.perform(staffGet(branchA, "/" + orderId + "/refunds", branchAStaffCookie)).andExpect(status().isNotFound());
        mockMvc.perform(staffPost(branchA, "/" + orderId + "/refunds", refundBody, branchAStaffCookie)).andExpect(status().isNotFound());

        // The order and its funds are untouched by every rejected attempt above.
        mockMvc.perform(staffGet(branchB, "/search?orderNumber=" + orderNumber, branchBStaffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].refundedQuantity").value(0))
                .andExpect(jsonPath("$.items[0].remainingRefundableQuantity").value(2))
                .andExpect(jsonPath("$.refunds", org.hamcrest.Matchers.hasSize(0)));
    }

    private String bootstrapStaffAdmin(String businessId, String branchId, String emailLocalPart) throws Exception {
        return StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, emailLocalPart + "@example.com");
    }

    private int fetchLatestOrderNumber(String branchId, String staffCookie) throws Exception {
        JsonNode queue = objectMapper.readTree(mockMvc.perform(inProgressGet(branchId, staffCookie))
                .andReturn()
                .getResponse()
                .getContentAsString());
        if (queue.size() > 0) {
            return queue.get(queue.size() - 1).get("orderNumber").asInt();
        }
        throw new IllegalStateException("No orders currently in progress for branch " + branchId);
    }

    private CheckedInVisit createPaidOrderWithOneItem(
            String businessId, String branchId, String qrToken, String productName, long priceMinorUnits, int quantity, String staffCookie)
            throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, productName, priceMinorUnits);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        addItem(visit, productId, quantity);
        payDraftOrder(visit, staffCookie);
        return visit;
    }

    private void addItem(CheckedInVisit visit, String productId, int quantity) throws Exception {
        mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated());
    }

    private JsonNode readJson(MockHttpServletRequestBuilder request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private int performConcurrentRefund(
            CountDownLatch start, String branchId, String orderId, String body, String staffCookie) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mockMvc.perform(staffPost(branchId, "/" + orderId + "/refunds", body, staffCookie))
                .andReturn()
                .getResponse()
                .getStatus();
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

    private MockHttpServletRequestBuilder inProgressGet(String branchId, String staffCookie) {
        return get("/api/staff/branches/{branchId}/orders/in-progress", branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
    }

    private MockHttpServletRequestBuilder staffGet(String branchId, String path, String staffCookie) {
        return get("/api/staff/branches/{branchId}/orders" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
    }

    private MockHttpServletRequestBuilder staffPost(String branchId, String path, String jsonBody, String staffCookie) {
        return post("/api/staff/branches/{branchId}/orders" + path, branchId)
                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
