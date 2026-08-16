package com.qrmenu.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.time.LocalDate;
import java.time.LocalTime;
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
        LocalTime now = LocalTime.now();
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
        LocalTime now = LocalTime.now();
        setTodayHours(branchId, staffCookie, now.minusHours(1), now.plusHours(1), false);

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = createDraftOrderWithOneItem(businessId, branchId, qrToken);

        createPaymentIntent(visit).andExpect(status().isCreated());
    }

    @Test
    void staffCanReadBackTheHoursTheyJustSet() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Readback Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hours-admin-4@example.com");
        setTodayHours(branchId, staffCookie, LocalTime.of(9, 0), LocalTime.of(22, 0), false);

        JsonNode hours = objectMapper.readTree(mockMvc.perform(get("/api/staff/branches/{branchId}/business-hours", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(hours).hasSize(1);
        assertThat(hours.get(0).get("dayOfWeek").asText()).isEqualTo(LocalDate.now().getDayOfWeek().name());
        assertThat(hours.get(0).get("closed").asBoolean()).isFalse();
    }

    private void setTodayHours(String branchId, String staffCookie, LocalTime opening, LocalTime closing, boolean closed) throws Exception {
        String dayOfWeek = LocalDate.now().getDayOfWeek().name();
        mockMvc.perform(post("/api/staff/branches/{branchId}/business-hours", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":[{\"dayOfWeek\":\"" + dayOfWeek + "\",\"openingTime\":\"" + opening + "\",\"closingTime\":\""
                                + closing + "\",\"closed\":" + closed + "}]}"))
                .andExpect(status().isOk());
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
