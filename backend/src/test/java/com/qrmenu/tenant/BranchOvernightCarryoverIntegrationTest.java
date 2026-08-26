package com.qrmenu.tenant;

import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TenantService.isWithinYesterdaysOvernightCarryOver's "still within yesterday's overnight
 * tail" branch - split out of BranchBusinessHoursFlowIntegrationTest because this scenario
 * needs a pinned instant, not real wall-clock time. That flag is only ever raised via a plain
 * {@code opening.isAfter(closing)} LocalTime comparison (no calendar awareness) - meaningful
 * only when opening/closing/"now" are all constructed relative to one *known* moment. The old
 * version built its fixture off real {@code LocalTime.now()} with a fixed 23:00 opening, which
 * silently stopped representing an overnight wrap (and started flaking) for ~65 real minutes
 * of any day - whenever "now" itself was already at/after ~22:55 Europe/Istanbul, "closing" (=
 * now + 5min) no longer had to be numerically less than 23:00.
 *
 * <p>Imports {@link FixedClockConfig}, which replaces the app's real Clock bean
 * ({@link TenantClockConfig}) with {@code Clock.fixed(...)} via a {@code @Primary} override -
 * a distinct Spring context from the rest of the suite's (cached separately, but still sharing
 * AbstractIntegrationTest's one static Postgres container) - so this is the one test class in
 * the suite where TenantService.isWithinConfiguredBusinessHours reads a pinned "now" instead of
 * the real clock. Production code/behavior is unchanged; only this test's own time source is.
 */
@Import(BranchOvernightCarryoverIntegrationTest.FixedClockConfig.class)
class BranchOvernightCarryoverIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    // An arbitrary but fixed local moment - well after local midnight, comfortably before
    // yesterday's contrived closing time below, nowhere near a year/month boundary that could
    // complicate the DayOfWeek arithmetic. The exact date/time carries no other significance.
    private static final LocalDateTime FIXED_LOCAL_NOW = LocalDateTime.of(2026, 1, 3, 0, 30, 0);
    private static final Instant FIXED_INSTANT = FIXED_LOCAL_NOW.atZone(ZONE).toInstant();
    private static final LocalTime FIXED_NOW = FIXED_LOCAL_NOW.toLocalTime();
    private static final DayOfWeek FIXED_TODAY = FIXED_LOCAL_NOW.getDayOfWeek();

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZONE);
        }
    }

    /** Overnight window (e.g. Monday 18:00-02:00) is stored under yesterday's DayOfWeek row -
     * a customer ordering just after midnight must still be allowed even if today's own row
     * is closed, since they're still inside yesterday night's carried-over window. */
    @Test
    void anOvernightWindowFromYesterdayStillAllowsOrderingJustAfterMidnight() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Overnight Carryover Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hours-admin-overnight@example.com");
        // Yesterday's row still has a few minutes left in its overnight tail (closing is 5
        // fixed minutes after the pinned "now"), so it must win even though today is
        // explicitly closed. Both days must be sent in the same request - setBranchBusinessHours
        // replaces the whole weekly schedule per call, so two sequential single-day calls would
        // overwrite each other instead of accumulating.
        setDaysHours(branchId, staffCookie,
                new DayHours(FIXED_TODAY.minus(1), LocalTime.of(23, 0), FIXED_NOW.plusMinutes(5), false),
                new DayHours(FIXED_TODAY, LocalTime.of(0, 0), LocalTime.of(23, 59), true));

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken);

        createPaymentIntent(visit).andExpect(status().isCreated());
    }

    private void setDaysHours(String branchId, String staffCookie, DayHours... entries) throws Exception {
        String daysJson = Arrays.stream(entries)
                .map(entry -> "{\"dayOfWeek\":\"" + entry.dayOfWeek().name() + "\",\"openingTime\":\"" + entry.opening()
                        + "\",\"closingTime\":\"" + entry.closing() + "\",\"closed\":" + entry.closed() + "}")
                .collect(Collectors.joining(",", "[", "]"));
        mockMvc.perform(post("/api/staff/branches/{branchId}/business-hours", branchId)
                        .cookie(new MockCookie(com.qrmenu.staffaccess.StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":" + daysJson + "}"))
                .andExpect(status().isOk());
    }

    private record DayHours(DayOfWeek dayOfWeek, LocalTime opening, LocalTime closing, boolean closed) {
    }

    private CheckedInVisit createDraftOrderWithOneItem(String businessId, String branchId, String qrToken) throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", 1000);
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCookie(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
