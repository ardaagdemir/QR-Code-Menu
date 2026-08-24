package com.qrmenu.platformadmin;

import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform Admin Panel (/api/platform-admin/**): session-authenticated PLATFORM_ADMIN only,
 * never the /internal/** shared-token API. Covers the decisions from the approved plan -
 * PLATFORM_ADMIN is treated as a real cross-business role (not scoped to its own
 * StaffUser.businessId), the panel can only assign BUSINESS_ADMIN/BRANCH_MANAGER/CASHIER,
 * and it can never create, promote to, or otherwise manage another PLATFORM_ADMIN account.
 */
class PlatformAdminFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void platformAdminCanCreateListAndToggleBusinesses() throws Exception {
        String homeBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Platform Admin Home Business");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, homeBusinessId, "pa-biz-create@example.com"));

        String newBusinessId = objectMapper
                .readTree(mockMvc.perform(post("/api/platform-admin/businesses")
                                .cookie(platformAdminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Brand New Business\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.active").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/api/platform-admin/businesses").cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + newBusinessId + "')]").exists());

        mockMvc.perform(post("/api/platform-admin/businesses/{id}/deactivate", newBusinessId).cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/platform-admin/businesses/{id}/activate", newBusinessId).cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void platformAdminIsNotScopedToItsOwnBusinessAndCanManageAnotherBusinessesBranchesAndStaff() throws Exception {
        String homeBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Platform Admin Home");
        String otherBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Some Other Business");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, homeBusinessId, "pa-cross-business@example.com"));

        // Manages a business that is not the one its own StaffUser row happens to carry.
        String branchId = objectMapper
                .readTree(mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/branches", otherBusinessId)
                                .cookie(platformAdminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Cross Business Branch\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/api/platform-admin/businesses/{businessId}/branches", otherBusinessId).cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(branchId));

        String staffUserId = objectMapper
                .readTree(mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/staff-users", otherBusinessId)
                                .cookie(platformAdminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"pa-created-admin@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"BUSINESS_ADMIN\",\"branchIds\":[\"" + branchId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.role").value("BUSINESS_ADMIN"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        // Role change, deactivate/activate, password reset all round-trip against the real login endpoint.
        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/role",
                                otherBusinessId,
                                staffUserId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"BRANCH_MANAGER\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/deactivate",
                                otherBusinessId,
                                staffUserId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pa-created-admin@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/activate",
                                otherBusinessId,
                                staffUserId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isNoContent());

        String newPassword = "brand-new-password-1";
        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/reset-password",
                                otherBusinessId,
                                staffUserId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + newPassword + "\",\"confirmNewPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pa-created-admin@example.com\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void platformAdminCannotCreatePlatformAdminOrPromoteAnyoneToIt() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "No New Platform Admins Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "pa-no-escalation@example.com"));

        mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/staff-users", businessId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sneaky-platform-admin@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"PLATFORM_ADMIN\",\"branchIds\":[]}"))
                .andExpect(status().isForbidden());

        String cashierEmail = "pa-cashier-target@example.com";
        String cashierId = objectMapper
                .readTree(mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/staff-users", businessId)
                                .cookie(platformAdminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/role", businessId, cashierId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PLATFORM_ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    /** Read-only is fine (list already includes PLATFORM_ADMIN rows - the panel is cross-business
     * and does need to see them to explain the account list), but every mutation against a
     * PLATFORM_ADMIN target must stay blocked: requireNonPlatformAdminTarget is the single choke
     * point behind deactivate/activate/role-change/reset-password (StaffAuthService), so this
     * covers all four rather than just deactivate/reset-password. */
    @Test
    void platformAdminCannotManageAnotherPlatformAdminAccountThroughThePanel() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Platform Admin Target Business");
        MockCookie actingPlatformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "pa-actor@example.com"));
        String targetPlatformAdminId = objectMapper
                .readTree(mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                                .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"pa-target@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"PLATFORM_ADMIN\",\"branchIds\":[]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        // Read is unaffected - the target still shows up in the panel's own listing.
        mockMvc.perform(get("/api/platform-admin/businesses/{businessId}/staff-users", businessId).cookie(actingPlatformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + targetPlatformAdminId + "')].role").value("PLATFORM_ADMIN"));

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/deactivate",
                                businessId,
                                targetPlatformAdminId)
                        .cookie(actingPlatformAdminCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/activate",
                                businessId,
                                targetPlatformAdminId)
                        .cookie(actingPlatformAdminCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/role",
                                businessId,
                                targetPlatformAdminId)
                        .cookie(actingPlatformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"BUSINESS_ADMIN\"}"))
                .andExpect(status().isForbidden());

        String newPassword = "brand-new-password-1";
        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/reset-password",
                                businessId,
                                targetPlatformAdminId)
                        .cookie(actingPlatformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + newPassword + "\",\"confirmNewPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isForbidden());

        // The target account is untouched by every rejected attempt - still PLATFORM_ADMIN, still able
        // to log in with its original password (reset-password never took effect).
        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pa-target@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void nonPlatformAdminStaffCannotReachThePlatformAdminPanel() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Regular Business Admin Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie businessAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "regular-admin@example.com"));

        mockMvc.perform(get("/api/platform-admin/businesses").cookie(businessAdminCookie)).andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerCannotReachThePlatformAdminPanel() throws Exception {
        mockMvc.perform(get("/api/platform-admin/businesses")).andExpect(status().isUnauthorized());
    }
}
