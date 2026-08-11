package com.qrmenu.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Section 7 (MVP scope): "Session/masa bazlı rate limiting" - flagged in the original
 * scope list but never wired up through Milestone 8; closed here as part of Milestone
 * 9's security hardening. In-memory only (Section 12: no Redis/external infra, same
 * single-instance assumption SseOrderStatusNotifier already makes) - a fixed-window
 * counter, scoped to /api/** only, skipping CORS preflight (OPTIONS - Milestone 6
 * learned this lesson once already) and SSE streams (long-lived single connections,
 * not a burst-of-requests pattern).
 *
 * <p>Three key tiers, in priority order, each with its own limit:
 * <ol>
 *   <li>Session cookie (qrmenu_session/qrmenu_staff_session) - a single browser tab
 *       making SESSION_LIMIT+ requests/window is unambiguously abusive, not organic use.
 *   <li>QR token, extracted from the check-in path itself (the ONE customer-facing
 *       mutation with no session yet) - literally "masa bazlı": a table's own repeated
 *       scans/refreshes are throttled without penalizing every other table.
 *   <li>Remote address, as a last-resort fallback for anything else unauthenticated -
 *       deliberately generous (IP_LIMIT), because a busy restaurant's shared WiFi/NAT
 *       means many *different* legitimate customers can share one address; this tier
 *       exists to catch a genuinely hostile single-source flood, not to police a
 *       lunch rush.
 * </ol>
 */
@Component
public class RateLimitFilter extends HttpFilter {

    private static final int SESSION_LIMIT = 120;
    private static final int TABLE_LIMIT = 30;
    private static final int IP_LIMIT = 300;
    private static final Pattern CHECKIN_PATH = Pattern.compile("^/api/qr/([^/]+)/visit$");

    private final Duration window;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.window = Duration.ofSeconds(windowSeconds);
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod()) || path.endsWith("/stream")) {
            chain.doFilter(request, response);
            return;
        }

        KeyAndLimit keyAndLimit = resolveKeyAndLimit(request, path);
        if (isOverLimit(keyAndLimit)) {
            response.setStatus(429);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Rate limit exceeded, slow down.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Unbounded growth guard - a counter whose window is more than one window old is dead weight, not active traffic. */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M")
    void evictStaleCounters() {
        long currentWindow = System.currentTimeMillis() / window.toMillis();
        counters.values().removeIf(counter -> counter.windowId < currentWindow - 1);
    }

    private boolean isOverLimit(KeyAndLimit keyAndLimit) {
        long currentWindow = System.currentTimeMillis() / window.toMillis();
        WindowCounter counter = counters.computeIfAbsent(keyAndLimit.key(), ignored -> new WindowCounter(currentWindow));
        return counter.incrementAndCheck(currentWindow, keyAndLimit.limit());
    }

    private KeyAndLimit resolveKeyAndLimit(HttpServletRequest request, String path) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("qrmenu_session".equals(cookie.getName()) || "qrmenu_staff_session".equals(cookie.getName())) {
                    return new KeyAndLimit(cookie.getName() + ":" + cookie.getValue(), SESSION_LIMIT);
                }
            }
        }
        Matcher checkinMatch = CHECKIN_PATH.matcher(path);
        if (checkinMatch.matches()) {
            return new KeyAndLimit("table:" + checkinMatch.group(1), TABLE_LIMIT);
        }
        return new KeyAndLimit("ip:" + request.getRemoteAddr(), IP_LIMIT);
    }

    private record KeyAndLimit(String key, int limit) {
    }

    private static final class WindowCounter {
        private volatile long windowId;
        private final AtomicLong count = new AtomicLong();

        WindowCounter(long windowId) {
            this.windowId = windowId;
        }

        synchronized boolean incrementAndCheck(long currentWindow, int max) {
            if (windowId != currentWindow) {
                windowId = currentWindow;
                count.set(0);
            }
            return count.incrementAndGet() > max;
        }
    }
}
