package com.qrmenu.payment.web;

import com.qrmenu.payment.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server provider callback - no cookie/session auth (a real provider isn't a
 * browser and carries no qrmenu_session cookie), authenticated purely by the
 * X-Mock-Signature HMAC over the raw body (Section 1.3). Binding the body as a plain
 * String (not a @RequestBody DTO) is what keeps it "raw, unparsed" ahead of signature
 * verification - Spring's StringHttpMessageConverter hands back the exact bytes with no
 * JSON deserialization in front of it. The mock adapter's simulate flow does not call
 * this endpoint over HTTP (MockPaymentSimulationDispatcher invokes
 * PaymentWebhookService directly, in-process) - this route exists so the mock's HTTP
 * shape matches what a real provider integration would look like, and so it can be
 * exercised directly (e.g. in tests, or manually).
 */
@RestController
@RequestMapping("/api/payments/webhook")
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    public PaymentWebhookController(PaymentWebhookService paymentWebhookService) {
        this.paymentWebhookService = paymentWebhookService;
    }

    @PostMapping("/mock")
    public ResponseEntity<Void> handleMockWebhook(@RequestBody String rawBody, @RequestHeader("X-Mock-Signature") String signature) {
        paymentWebhookService.handleIncomingWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
