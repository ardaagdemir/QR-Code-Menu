package com.qrmenu.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no Spring context/Testcontainers - RateLimitFilter has no framework
 * dependency beyond @Value on its constructor, so it's directly instantiable) for
 * Milestone 9's rate limiter: the session tier's exact threshold, that a different
 * session is unaffected, and that OPTIONS/non-/api/ requests are never limited.
 */
class RateLimitFilterTest {

    @Test
    void aSessionExceedingTheLimitGetsA429ButADifferentSessionIsUnaffected() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(60);

        // SESSION_LIMIT is 120 - the 121st request in the same window must be rejected.
        for (int i = 0; i < 120; i++) {
            HttpServletRequest request = apiRequestWithSessionCookie("session-a");
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            verify(chain, times(1)).doFilter(request, response);
            verify(response, never()).setStatus(anyInt());
        }

        HttpServletRequest overLimitRequest = apiRequestWithSessionCookie("session-a");
        HttpServletResponse overLimitResponse = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(overLimitResponse.getWriter()).thenReturn(new PrintWriter(body));
        FilterChain overLimitChain = mock(FilterChain.class);
        filter.doFilter(overLimitRequest, overLimitResponse, overLimitChain);
        verify(overLimitChain, never()).doFilter(overLimitRequest, overLimitResponse);
        verify(overLimitResponse).setStatus(429);
        assertThat(body.toString()).contains("Too Many Requests");

        // A different session's own budget is untouched by session-a's usage.
        HttpServletRequest otherSessionRequest = apiRequestWithSessionCookie("session-b");
        HttpServletResponse otherSessionResponse = mock(HttpServletResponse.class);
        FilterChain otherSessionChain = mock(FilterChain.class);
        filter.doFilter(otherSessionRequest, otherSessionResponse, otherSessionChain);
        verify(otherSessionChain, times(1)).doFilter(otherSessionRequest, otherSessionResponse);
        verify(otherSessionResponse, never()).setStatus(anyInt());
    }

    @Test
    void corsPreflightAndNonApiPathsAreNeverRateLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(60);

        for (int i = 0; i < 500; i++) {
            HttpServletRequest optionsRequest = mock(HttpServletRequest.class);
            when(optionsRequest.getRequestURI()).thenReturn("/api/branches/some-branch/menu");
            when(optionsRequest.getMethod()).thenReturn("OPTIONS");
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(optionsRequest, response, chain);
            verify(chain, times(1)).doFilter(optionsRequest, response);
        }

        for (int i = 0; i < 500; i++) {
            HttpServletRequest actuatorRequest = mock(HttpServletRequest.class);
            when(actuatorRequest.getRequestURI()).thenReturn("/actuator/health");
            when(actuatorRequest.getMethod()).thenReturn("GET");
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(actuatorRequest, response, chain);
            verify(chain, times(1)).doFilter(actuatorRequest, response);
        }
    }

    private static HttpServletRequest apiRequestWithSessionCookie(String sessionValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/table-visits/some-visit/cart/items");
        when(request.getMethod()).thenReturn("POST");
        when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("qrmenu_session", sessionValue)});
        return request;
    }
}
