package com.qrmenu.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.payment.repository.PaymentRepository;
import com.qrmenu.shared.outbox.OutboxEvent;
import com.qrmenu.shared.outbox.OutboxEventRepository;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static com.qrmenu.support.AbstractIntegrationTest.TEST_PAYMENT_WEBHOOK_SECRET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Milestone 5's mock payment flow end-to-end: CREATED -> PROCESSING via
 * createPaymentIntent, then SUCCEEDED/FAILED only via the async mock webhook dispatch
 * (never directly from the "simulate" trigger - Section 2), webhook signature
 * verification + idempotency, the authoritative ordering-allowed gate, and the
 * OrderPaid outbox write.
 */
class PaymentFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void successfulMockPaymentMovesPaymentAndOrderToTerminalStatesAndWritesAnOutboxEvent() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Payment Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken, 12500);

        MvcResult intentResult = createPaymentIntent(visit)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.amountMinorUnits").value(12500))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andReturn();
        JsonNode intentBody = objectMapper.readTree(intentResult.getResponse().getContentAsString());
        String paymentId = intentBody.get("paymentId").asText();
        String orderId = intentBody.get("orderId").asText();

        triggerMockOutcome(visit, paymentId, "SUCCEEDED").andExpect(status().isAccepted());

        JsonNode finalStatus = pollPaymentStatus(visit, paymentId, "SUCCEEDED");
        assertThat(finalStatus.get("orderStatus").asText()).isEqualTo("IN_KITCHEN");

        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(UUID.fromString(orderId)))
                .toList();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("OrderPaid");
        assertThat(outboxEvents.get(0).getPayload()).contains(orderId);
    }

    @Test
    void failedMockPaymentMovesOrderToPaymentFailedAndAllowsARetry() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Retry Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken, 5000);

        JsonNode firstIntent = objectMapper.readTree(createPaymentIntent(visit)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String firstPaymentId = firstIntent.get("paymentId").asText();

        triggerMockOutcome(visit, firstPaymentId, "FAILED").andExpect(status().isAccepted());
        JsonNode failedStatus = pollPaymentStatus(visit, firstPaymentId, "FAILED");
        assertThat(failedStatus.get("orderStatus").asText()).isEqualTo("PAYMENT_FAILED");

        // Section 6, Payment state machine: PAYMENT_FAILED -> AWAITING_PAYMENT retry.
        JsonNode retryIntent = objectMapper.readTree(createPaymentIntent(visit)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String retryPaymentId = retryIntent.get("paymentId").asText();
        assertThat(retryPaymentId).isNotEqualTo(firstPaymentId);

        triggerMockOutcome(visit, retryPaymentId, "SUCCEEDED").andExpect(status().isAccepted());
        JsonNode succeededStatus = pollPaymentStatus(visit, retryPaymentId, "SUCCEEDED");
        assertThat(succeededStatus.get("orderStatus").asText()).isEqualTo("IN_KITCHEN");
    }

    @Test
    void aDuplicateWebhookDeliveryIsANoOpAndAnInvalidSignatureIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Webhook Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken, 3000);
        JsonNode intent = objectMapper.readTree(createPaymentIntent(visit)
                .andReturn()
                .getResponse()
                .getContentAsString());
        String paymentId = intent.get("paymentId").asText();
        String providerPaymentIntentId = fetchProviderPaymentIntentId(paymentId);

        String rawBody = "{\"eventId\":\"evt-" + paymentId + "\",\"providerPaymentIntentId\":\"" + providerPaymentIntentId
                + "\",\"outcome\":\"SUCCEEDED\"}";
        String signature = hmacSha256Hex(rawBody, TEST_PAYMENT_WEBHOOK_SECRET);

        mockMvc.perform(post("/api/payments/webhook/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Mock-Signature", "not-the-real-signature")
                        .content(rawBody))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/payments/webhook/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Mock-Signature", signature)
                        .content(rawBody))
                .andExpect(status().isOk());
        pollPaymentStatus(visit, paymentId, "SUCCEEDED");

        // Same event, delivered again (network retry) - must be a silent no-op, not an error.
        mockMvc.perform(post("/api/payments/webhook/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Mock-Signature", signature)
                        .content(rawBody))
                .andExpect(status().isOk());
        JsonNode statusAfterDuplicate = objectMapper.readTree(mockMvc.perform(withCookie(
                                get("/api/table-visits/{tableVisitId}/payments/{paymentId}", visit.tableVisitId(), paymentId), visit))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(statusAfterDuplicate.get("paymentStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(statusAfterDuplicate.get("orderStatus").asText()).isEqualTo("IN_KITCHEN");
    }

    @Test
    void payingWhenTheBranchIsNotAcceptingOrdersIsRejectedWithConflict() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Closed Business");
        JsonNode branch = objectMapper.readTree(mockMvc.perform(post("/internal/businesses/{businessId}/branches", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kapalı Şube\",\"orderingEnabled\":false}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String branchId = branch.get("id").asText();
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken, 2000);

        createPaymentIntent(visit).andExpect(status().isConflict());
    }

    @Test
    void startingPaymentForAnEmptyCartIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Empty Cart Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        createPaymentIntent(visit).andExpect(status().isNotFound());
    }

    @Test
    void paymentOperationsWithAnotherVisitsSessionCookieAreRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Ownership Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableAId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa A");
        String tableBId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa B");
        String qrTokenA = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableAId);
        String qrTokenB = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableBId);
        CheckedInVisit visitA = createDraftOrderWithOneItem(businessId, branchId, qrTokenA, 1500);
        CheckedInVisit visitB = TenantFixtures.checkIn(mockMvc, objectMapper, qrTokenB);

        mockMvc.perform(post("/api/table-visits/{tableVisitId}/payments", visitA.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visitB.sessionCookieValue())))
                .andExpect(status().isNotFound());
    }

    private CheckedInVisit createDraftOrderWithOneItem(String businessId, String branchId, String qrToken, long priceMinorUnits)
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
        return visit;
    }

    private org.springframework.test.web.servlet.ResultActions createPaymentIntent(CheckedInVisit visit) throws Exception {
        return mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId()), visit));
    }

    private org.springframework.test.web.servlet.ResultActions triggerMockOutcome(
            CheckedInVisit visit, String paymentId, String outcome) throws Exception {
        return mockMvc.perform(withCookie(
                        post("/api/table-visits/{tableVisitId}/payments/{paymentId}/mock-outcome", visit.tableVisitId(), paymentId),
                        visit)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"" + outcome + "\"}"));
    }

    private String fetchProviderPaymentIntentId(String paymentId) {
        return paymentRepository.findById(UUID.fromString(paymentId)).orElseThrow().getProviderPaymentIntentId();
    }

    private JsonNode pollPaymentStatus(CheckedInVisit visit, String paymentId, String expectedPaymentStatus) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(withCookie(
                            get("/api/table-visits/{tableVisitId}/payments/{paymentId}", visit.tableVisitId(), paymentId), visit))
                    .andExpect(status().isOk())
                    .andReturn();
            last = objectMapper.readTree(result.getResponse().getContentAsString());
            if (expectedPaymentStatus.equals(last.get("paymentStatus").asText())) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for paymentStatus=" + expectedPaymentStatus + ", last=" + last);
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }

    private static String hmacSha256Hex(String rawBody, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
    }
}
