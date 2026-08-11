package com.qrmenu.tenant.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Shared-secret guard for /internal/**. Not Spring Security - a full authentication
 * framework would be scope creep for a single internal bootstrap endpoint that real
 * StaffUser/PLATFORM_ADMIN auth (Milestone 8) will eventually replace or front.
 */
class InternalAdminAuthFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Internal-Admin-Token";

    private final String expectedToken;

    InternalAdminAuthFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER_NAME);
        if (expectedToken == null || expectedToken.isBlank() || provided == null || !constantTimeEquals(provided)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing internal admin token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String provided) {
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
