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

/** Gap-analysis #7 (product-requirements.md Section 18.1): staff announcements. */
class AnnouncementFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void businessAdminCanCreateListAndEndAnAllBranchesAnnouncement() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Announce Business 1");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "announce-admin-1@example.com");

        String announcementId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/announcements")
                                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Yeni menü\",\"message\":\"Yeni menü yayında\",\"target\":\"ALL_BRANCHES\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/staff/announcements")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(list).hasSize(1);

        JsonNode active = objectMapper.readTree(mockMvc.perform(get("/api/staff/announcements/active")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(active).hasSize(1);

        mockMvc.perform(post("/api/staff/announcements/{id}/end", announcementId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk());

        JsonNode activeAfterEnd = objectMapper.readTree(mockMvc.perform(get("/api/staff/announcements/active")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(activeAfterEnd).isEmpty();
    }

    @Test
    void selectedBranchesTargetIsOnlyVisibleToStaffScopedToThoseBranches() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Announce Business 2");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "announce-admin-2@example.com");

        mockMvc.perform(post("/api/staff/announcements")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Şube A duyurusu\",\"message\":\"Sadece A\",\"target\":\"SELECTED_BRANCHES\",\"branchIds\":[\""
                                + branchA + "\"]}"))
                .andExpect(status().isCreated());

        String managerAEmail = "announce-manager-a@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + managerAEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BRANCH_MANAGER\",\"branchIds\":[\"" + branchA + "\"]}"))
                .andExpect(status().isCreated());
        String managerACookie = StaffFixtures.login(mockMvc, managerAEmail);

        String managerBEmail = "announce-manager-b@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + managerBEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BRANCH_MANAGER\",\"branchIds\":[\"" + branchB + "\"]}"))
                .andExpect(status().isCreated());
        String managerBCookie = StaffFixtures.login(mockMvc, managerBEmail);

        JsonNode activeForA = objectMapper.readTree(mockMvc.perform(get("/api/staff/announcements/active")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, managerACookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(activeForA).hasSize(1);

        JsonNode activeForB = objectMapper.readTree(mockMvc.perform(get("/api/staff/announcements/active")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, managerBCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(activeForB).isEmpty();
    }

    @Test
    void selectedBranchesWithNoBranchIdsIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Announce Business 3");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "announce-admin-3@example.com");

        mockMvc.perform(post("/api/staff/announcements")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Boş hedef\",\"message\":\"...\",\"target\":\"SELECTED_BRANCHES\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffWithoutAnnouncementManagePermissionCannotCreateButCanReadActive() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Announce Business 4");
        String kitchenEmail = "announce-kitchen-1@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + kitchenEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"KITCHEN_STAFF\"}"))
                .andExpect(status().isCreated());
        String kitchenCookie = StaffFixtures.login(mockMvc, kitchenEmail);

        mockMvc.perform(post("/api/staff/announcements")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, kitchenCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"message\":\"y\",\"target\":\"ALL_BRANCHES\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/staff/announcements/active")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, kitchenCookie)))
                .andExpect(status().isOk());
    }
}
