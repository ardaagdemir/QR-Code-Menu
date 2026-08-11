package com.qrmenu.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * "Gerçekçi mock akışı" (Section 2): never shortcuts to an instant SUCCEEDED.
 * createPaymentIntent only stands up a provider-side intent id - PaymentService is the
 * one that moves the Payment CREATED -> PROCESSING after calling this. The actual
 * SUCCEEDED/FAILED transition only ever happens via a signed webhook body processed by
 * PaymentWebhookService, whether that body arrives over real HTTP
 * (PaymentWebhookController) or via the mock simulate flow's async dispatch
 * (MockPaymentSimulationDispatcher) - both go through verifyWebhookSignature/
 * parseWebhookEvent below, never around them.
 *
 * The only provider today, so it's the default when `payment.provider` is unset;
 * `havingValue = "mock"` keeps a future real adapter (selected by the same config key)
 * from having two competing PaymentProviderPort beans.
 */
@Component
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProviderAdapter implements PaymentProviderPort {

    private static final String PROVIDER_NAME = "mock";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public MockPaymentProviderAdapter(@Value("${payment.mock.webhook-secret}") String webhookSecret, ObjectMapper objectMapper) {
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public ProviderPaymentIntent createPaymentIntent(long amountMinorUnits) {
        return new ProviderPaymentIntent("mock_pi_" + UUID.randomUUID());
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank() || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        String expected = sign(rawBody);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ParsedWebhookEvent parseWebhookEvent(String rawBody) {
        MockWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, MockWebhookPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed mock webhook payload", e);
        }
        return new ParsedWebhookEvent(
                payload.eventId(), payload.providerPaymentIntentId(), WebhookOutcome.valueOf(payload.outcome()));
    }

    /**
     * Milestone 7: unlike payment creation, the mock refund path is a synchronous,
     * always-succeeds call - RefundService moves Refund through
     * REQUESTED -> PROCESSING -> COMPLETED itself right after calling this. There is
     * no separate async webhook dance here (contrast MockPaymentSimulationDispatcher):
     * the "gerçekçi mock akışı" requirement (Section 2) was specifically about not
     * shortcutting *payment* SUCCEEDED, since that's the money-in commitment point
     * that must be idempotent/signature-verified; a mock provider's refund
     * acknowledgement carries no equivalent risk here.
     */
    @Override
    public void refund(String providerPaymentIntentId, long amountMinorUnits) {
        // No-op: the mock provider always accepts a refund request immediately.
    }

    /**
     * Builds and signs a webhook body for a simulated outcome - used only by
     * MockPaymentSimulationDispatcher (the "hosted payment screen" trigger), never by
     * the frontend directly (Section 2: the frontend's "success" click must not itself
     * flip the order to PAID).
     */
    SignedWebhookPayload buildSignedWebhookPayload(String providerPaymentIntentId, WebhookOutcome outcome) {
        String eventId = "mock_evt_" + UUID.randomUUID();
        String rawBody;
        try {
            rawBody = objectMapper.writeValueAsString(new MockWebhookPayload(eventId, providerPaymentIntentId, outcome.name()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build mock webhook payload", e);
        }
        return new SignedWebhookPayload(rawBody, sign(rawBody));
    }

    private String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute mock webhook signature", e);
        }
    }
}
