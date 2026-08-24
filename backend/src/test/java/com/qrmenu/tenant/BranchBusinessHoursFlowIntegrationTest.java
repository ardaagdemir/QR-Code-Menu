package com.qrmenu.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #4 (product-requirements.md Section 12.2): per-day BranchBusinessHours
 * replacing the old single openingTime/closingTime pair - a day with no configured row
 * stays unrestricted (only orderingEnabled applies, same default as before), a
 * `closed=true` row blocks ordering outright regardless of hours, and a configured
 * window blocks/allows exactly like the old single-pair check did.
 */
class BranchBusinessHoursFlowIntegrationTest extends AbstractIntegrationTest {

    // None of these fixtures set an explicit Branch.timezone, so TenantService.
    // assertOrderingCurrentlyAllowed resolves "now"/"today" via the business's
    // defaultTimeZone ("Europe/Istanbul" - Business's single-arg constructor), never a
    // bare UTC/JVM-default guess. A bare LocalDate.now()/LocalTime.now() here would
    // happen to match production today only because this environment's own JVM zone is
    // already Europe/Istanbul - pinning both to the same explicit zone keeps this
    // suite correct regardless of where it actually runs (e.g. a UTC-configured CI box).
    private static final ZoneId BUSINESS_DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    @Test
    void aBranchWithNoConfiguredHoursIsUnrestricted() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "No Hours Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken);

        createPaymentIntent(visit).andExpect(status().isCreated());
    }

    @Test
    void aClosedTodayRowBlocksOrderingRegardlessOfHours() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Closed Today Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hours-admin-1@example.com");
        setTodayHours(branchId, staffCookie, LocalTime.of(0, 0), LocalTime.of(23, 59), true);

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken);

        createPaymentIntent(visit).andExpect(status().isConflict());
    }

    @Test
    void anHoursWindowThatExcludesNowBlocksOrdering() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Outside Hours Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hours-admin-2@example.com");
        LocalTime now = LocalTime.now(BUSINESS_DEFAULT_ZONE);
        setTodayHours(branchId, staffCookie, now.plusHours(1), now.plusHours(2), false);

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken);

        createPaymentIntent(visit).andExpect(status().isConflict());
    }

    @Test
    void anHoursWindowThatIncludesNowAllowsOrdering() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Within Hours Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hours-admin-3@example.com");
        LocalTime now = LocalTime.now(BUSINESS_DEFAULT_ZONE);
        setTodayHours(branchId, staffCookie, now.minusHours(1), now.plusHours(1), false);

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken);

        createPaymentIntent(visit).andExpect(status().isCreated());
    }

    @Test
    void staffCanSaveAndReadBackAnOvernightHoursWindow() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Readback Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hours-admin-4@example.com");
        setTodayHours(branchId, staffCookie, LocalTime.of(18, 0), LocalTime.of(2, 0), false);

        JsonNode hours = objectMapper.readTree(mockMvc.perform(get("/api/staff/branches/{branchId}/business-hours", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(hours).hasSize(1);
        assertThat(hours.get(0).get("dayOfWeek").asText()).isEqualTo(LocalDate.now(BUSINESS_DEFAULT_ZONE).getDayOfWeek().name());
        assertThat(hours.get(0).get("closed").asBoolean()).isFalse();
        assertThat(hours.get(0).get("openingTime").asText()).startsWith("18:00");
        assertThat(hours.get(0).get("closingTime").asText()).startsWith("02:00");
    }

    // The "yesterday's overnight window is still within its carried-over tail" scenario
    // moved to BranchOvernightCarryoverIntegrationTest, which pins a Clock.fixed(...) instant
    // instead of building its fixture off real LocalTime.now() - see that class's Javadoc for
    // why (this test used to flake for ~65 real minutes of any day near local midnight).

    /** Once yesterday's overnight window has actually finished (its closing time already
     * passed), today's own schedule takes back over - a `closed=true` today must block. */
    @Test
    void todaysScheduleTakesOverOnceYesterdaysOvernightWindowHasEnded() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Overnight Ended Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hours-admin-6@example.com");
        LocalTime now = LocalTime.now(BUSINESS_DEFAULT_ZONE);
        DayOfWeek today = LocalDate.now(BUSINESS_DEFAULT_ZONE).getDayOfWeek();
        // Yesterday's overnight window already closed a few minutes ago, so its carryover
        // no longer applies - only today's own (closed) row governs. Both days in one
        // request, same reason as above.
        setDaysHours(branchId, staffCookie,
                new DayHours(today.minus(1), LocalTime.of(23, 0), now.minusMinutes(5), false),
                new DayHours(today, LocalTime.of(0, 0), LocalTime.of(23, 59), true));

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken);

        createPaymentIntent(visit).andExpect(status().isConflict());
    }

    /** The gate is re-evaluated live at payment time, not cached from check-in - staff
     * toggling ordering off mid-visit must reject a payment attempt on an already-checked-in
     * TableVisit, not just block brand-new check-ins. */
    @Test
    void togglingOrderingOffMidVisitBlocksAnAlreadyCheckedInCustomersPayment() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Mid Visit Toggle Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hours-admin-7@example.com");
        MockCookie staffMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken);

        mockMvc.perform(post("/api/staff/branches/{branchId}/ordering-enabled", branchId)
                        .cookie(staffMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        createPaymentIntent(visit).andExpect(status().isConflict());
    }

    /** The gate must evaluate hours in the branch's own timezone override, not the
     * business's default - proven by using a zone far enough from the business default
     * (Europe/Istanbul) that a naive "server/business zone" evaluation would land on a
     * different local time/day and reject a window that should actually be open. */
    @Test
    void aBranchsOwnTimezoneOverrideGovernsTheOrderingHoursGate() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Branch Zone Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hours-admin-8@example.com");
        MockCookie staffMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);

        ZoneId branchZone = ZoneId.of("Pacific/Honolulu");
        mockMvc.perform(post("/api/staff/branches/{branchId}/timezone", branchId)
                        .cookie(staffMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"" + branchZone.getId() + "\"}"))
                .andExpect(status().isOk());

        // A window built from Istanbul's "now" - if the gate wrongly evaluated hours in the
        // business's default zone instead of the branch's own, this would (usually) include
        // "now" and the bug would go unnoticed; asserting against the *excluding* window
        // built from Honolulu's own now/day is what actually pins the branch-zone behavior.
        LocalTime honoluluNow = LocalTime.now(branchZone);
        setDayHours(branchId, staffCookie, LocalDate.now(branchZone).getDayOfWeek(),
                honoluluNow.plusHours(2), honoluluNow.plusHours(3), false);

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken);

        // Outside the Honolulu-local window -> rejected. If the gate had used the business's
        // Europe/Istanbul default instead, "now" there is ~13h ahead and could easily land
        // inside a completely different (wrong) window, silently allowing the payment.
        createPaymentIntent(visit).andExpect(status().isConflict());
    }

    private void setTodayHours(String branchId, String staffCookie, LocalTime opening, LocalTime closing, boolean closed) throws Exception {
        setDayHours(branchId, staffCookie, LocalDate.now(BUSINESS_DEFAULT_ZONE).getDayOfWeek(), opening, closing, closed);
    }

    private void setDayHours(
            String branchId, String staffCookie, DayOfWeek dayOfWeek, LocalTime opening, LocalTime closing, boolean closed)
            throws Exception {
        setDaysHours(branchId, staffCookie, new DayHours(dayOfWeek, opening, closing, closed));
    }

    /** setBranchBusinessHours replaces the entire weekly schedule per call (delete-then-
     * insert-all) - every day that needs to stay configured must be sent together in one
     * request, not accumulated across separate calls. */
    private void setDaysHours(String branchId, String staffCookie, DayHours... entries) throws Exception {
        String daysJson = Arrays.stream(entries)
                .map(entry -> "{\"dayOfWeek\":\"" + entry.dayOfWeek().name() + "\",\"openingTime\":\"" + entry.opening()
                        + "\",\"closingTime\":\"" + entry.closing() + "\",\"closed\":" + entry.closed() + "}")
                .collect(Collectors.joining(",", "[", "]"));
        mockMvc.perform(post("/api/staff/branches/{branchId}/business-hours", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":" + daysJson + "}"))
                .andExpect(status().isOk());
    }

    private record DayHours(DayOfWeek dayOfWeek, LocalTime opening, LocalTime closing, boolean closed) {
    }

    private CheckedInVisit createDraftOrderWithOneItem(String businessId, String branchId, String qrToken) throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", 1000, 10);
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
