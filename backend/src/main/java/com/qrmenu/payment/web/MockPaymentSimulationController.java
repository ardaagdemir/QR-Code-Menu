package com.qrmenu.payment.web;

import com.qrmenu.customersession.SessionCookieSupport;
import com.qrmenu.payment.MockPaymentSimulationService;
import com.qrmenu.payment.web.dto.MockOutcomeRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dev/mock "hosted payment screen" trigger (Section 2, mock flow step 2) -
 * customer-web's mock checkout sheet calls this to simulate the customer completing or
 * abandoning the provider's hosted page. Returns 202 immediately: the actual
 * SUCCEEDED/FAILED transition happens moments later via a separate async call (Section
 * 2), so the caller must poll PaymentController.getPaymentStatus to observe it. Only
 * registered when payment.provider=mock - a real provider has no equivalent endpoint.
 */
@RestController
@RequestMapping("/api/table-visits/{tableVisitId}/payments/{paymentId}/mock-outcome")
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentSimulationController {

    private final MockPaymentSimulationService mockPaymentSimulationService;

    public MockPaymentSimulationController(MockPaymentSimulationService mockPaymentSimulationService) {
        this.mockPaymentSimulationService = mockPaymentSimulationService;
    }

    @PostMapping
    public ResponseEntity<Void> triggerOutcome(
            @PathVariable UUID tableVisitId,
            @PathVariable UUID paymentId,
            @CookieValue(name = SessionCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody MockOutcomeRequest request) {
        UUID sessionId = SessionCookieSupport.parseSessionId(sessionCookie);
        mockPaymentSimulationService.triggerOutcome(tableVisitId, sessionId, paymentId, request.outcome());
        return ResponseEntity.accepted().build();
    }
}
