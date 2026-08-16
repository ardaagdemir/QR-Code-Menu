package com.qrmenu.announcement;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gap-analysis #16: announcements remain available, restricted to the active branch. */
class AnnouncementFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void businessAdminCanCreateListAndEndAnAnnouncementOnlyInTheActiveBranch() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Announcement Scope");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");
        MockCookie cookieA = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchA, "announcement-a@example.com", "BUSINESS_ADMIN"));
        MockCookie cookieB = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchB, "announcement-b@example.com", "BUSINESS_ADMIN"));

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/staff/announcements")
                        .cookie(cookieA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Şube A\",\"message\":\"Yalnız A\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(created.get("target").asText()).isEqualTo("SELECTED_BRANCHES");
        assertThat(created.get("branchIds").get(0).asText()).isEqualTo(branchA);

        JsonNode listA = responseJson(get("/api/staff/announcements").cookie(cookieA));
        JsonNode listB = responseJson(get("/api/staff/announcements").cookie(cookieB));
        JsonNode activeA = responseJson(get("/api/staff/announcements/active").cookie(cookieA));
        JsonNode activeB = responseJson(get("/api/staff/announcements/active").cookie(cookieB));
        assertThat(listA).hasSize(1);
        assertThat(activeA).hasSize(1);
        assertThat(listB).isEmpty();
        assertThat(activeB).isEmpty();

        String announcementId = created.get("id").asText();
        mockMvc.perform(post("/api/staff/announcements/{id}/end", announcementId).cookie(cookieB))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/announcements/{id}/end", announcementId).cookie(cookieA))
                .andExpect(status().isOk());
        assertThat(responseJson(get("/api/staff/announcements/active").cookie(cookieA))).isEmpty();
    }

    @Test
    void crossBranchTargetsAreRejectedButCashierCanReadActiveBranchAnnouncements() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Announcement Guard");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "announcement-admin@example.com", "BUSINESS_ADMIN"));
        MockCookie cashierCookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "announcement-cashier@example.com", "CASHIER"));

        mockMvc.perform(post("/api/staff/announcements")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Zincir\",\"message\":\"Mesaj\",\"target\":\"ALL_BRANCHES\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/announcements")
                        .cookie(cashierCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"message\":\"y\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/announcements/active").cookie(cashierCookie))
                .andExpect(status().isOk());
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return objectMapper.readTree(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static MockCookie cookie(String value) {
        return new MockCookie(StaffCookieSupport.COOKIE_NAME, value);
    }
}
