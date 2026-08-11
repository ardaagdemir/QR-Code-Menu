package com.qrmenu.payment.web;

import com.qrmenu.customersession.SessionCookieSupport;
import com.qrmenu.payment.PaymentIntentView;
import com.qrmenu.payment.PaymentService;
import com.qrmenu.payment.PaymentStatusView;
import com.qrmenu.payment.web.dto.PaymentIntentResponse;
import com.qrmenu.payment.web.dto.PaymentStatusResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, anonymous, customer-facing payment API - same cookie-ownership pattern as
 * CartController (Section 5: the qrmenu_session cookie, not the tableVisitId in the
 * URL, is the access credential).
 */
@RestController
@RequestMapping("/api/table-visits/{tableVisitId}/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentIntentResponse> createPaymentIntent(
            @PathVariable UUID tableVisitId,
            @CookieValue(name = SessionCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        UUID sessionId = SessionCookieSupport.parseSessionId(sessionCookie);
        PaymentIntentView view = paymentService.createPaymentIntent(tableVisitId, sessionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(view));
    }

    @GetMapping("/{paymentId}")
    public PaymentStatusResponse getPaymentStatus(
            @PathVariable UUID tableVisitId,
            @PathVariable UUID paymentId,
            @CookieValue(name = SessionCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        UUID sessionId = SessionCookieSupport.parseSessionId(sessionCookie);
        PaymentStatusView view = paymentService.getPaymentStatus(tableVisitId, sessionId, paymentId);
        return new PaymentStatusResponse(view.paymentId(), view.orderId(), view.paymentStatus(), view.orderStatus());
    }

    private static PaymentIntentResponse toResponse(PaymentIntentView view) {
        return new PaymentIntentResponse(view.paymentId(), view.orderId(), view.status(), view.amountMinorUnits(), view.provider());
    }
}
