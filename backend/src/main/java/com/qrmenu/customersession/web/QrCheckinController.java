package com.qrmenu.customersession.web;

import com.qrmenu.customersession.CheckInResult;
import com.qrmenu.customersession.CustomerSessionService;
import com.qrmenu.customersession.SessionCookieSupport;
import com.qrmenu.customersession.TableVisit;
import com.qrmenu.customersession.web.dto.TableVisitResponse;
import com.qrmenu.tenant.TableReference;
import com.qrmenu.tenant.TenantService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, anonymous, customer-facing QR check-in: scanning a table's QR resolves it via
 * TenantService (tenant module, cross-module call through its public service only - see
 * ModuleBoundaryTest) and starts/continues a TableVisit for the caller's
 * AnonymousCustomerSession (Section 5).
 */
@RestController
@RequestMapping("/api/qr")
public class QrCheckinController {

    private static final Duration SESSION_COOKIE_MAX_AGE = Duration.ofDays(30);

    private final TenantService tenantService;
    private final CustomerSessionService customerSessionService;

    public QrCheckinController(TenantService tenantService, CustomerSessionService customerSessionService) {
        this.tenantService = tenantService;
        this.customerSessionService = customerSessionService;
    }

    @PostMapping("/{token}/visit")
    public ResponseEntity<TableVisitResponse> checkIn(
            @PathVariable String token,
            @CookieValue(name = SessionCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            HttpServletResponse response) {

        TableReference tableReference = tenantService.resolveActiveQrToken(token);
        CheckInResult result =
                customerSessionService.checkIn(tableReference, SessionCookieSupport.parseSessionId(sessionCookie));

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.sessionId()).toString());

        TableVisit visit = result.visit();
        return ResponseEntity.ok(new TableVisitResponse(
                visit.getId(),
                tableReference.businessId(),
                tableReference.branchId(),
                tableReference.tableId(),
                tableReference.businessName(),
                tableReference.branchName(),
                tableReference.tableLabel(),
                visit.getStartedAt(),
                visit.getGuestCount()));
    }

    private ResponseCookie sessionCookie(UUID sessionId) {
        return ResponseCookie.from(SessionCookieSupport.COOKIE_NAME, sessionId.toString())
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(SESSION_COOKIE_MAX_AGE)
                .build();
    }
}
