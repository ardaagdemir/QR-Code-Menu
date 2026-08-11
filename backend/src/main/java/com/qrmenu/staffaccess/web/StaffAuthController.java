package com.qrmenu.staffaccess.web;

import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffAuthService.LoginResult;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.staffaccess.web.dto.LoginRequest;
import com.qrmenu.staffaccess.web.dto.StaffContextResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * StaffUser login/logout/session-check (Section 4, staff-web screen #1). Cookie-based,
 * same shape as the customer side's qrmenu_session (QrCheckinController) - HttpOnly/
 * Secure/SameSite - but its own name and TTL (StaffAuthService.SESSION_TTL).
 */
@RestController
@RequestMapping("/api/staff/auth")
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    public StaffAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<StaffContextResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = staffAuthService.login(request.email(), request.password());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.sessionId(), StaffAuthService.SESSION_TTL).toString());
        return ResponseEntity.ok(toResponse(result.context()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie, HttpServletResponse response) {
        staffAuthService.logout(StaffCookieSupport.parseSessionId(sessionCookie));
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(null, Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public StaffContextResponse me(@CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie));
        return toResponse(context);
    }

    private static StaffContextResponse toResponse(StaffContext context) {
        return new StaffContextResponse(
                context.staffUserId(), context.businessId(), context.email(), context.role().name(), context.branchIds().stream().toList());
    }

    private ResponseCookie sessionCookie(UUID sessionId, Duration maxAge) {
        return ResponseCookie.from(StaffCookieSupport.COOKIE_NAME, sessionId == null ? "" : sessionId.toString())
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
