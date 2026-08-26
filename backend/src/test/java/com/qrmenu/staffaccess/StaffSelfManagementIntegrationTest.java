package com.qrmenu.staffaccess;

import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Self-management guardrails on top of business-scoped and platform-admin staff management:
 * neither entry point may deactivate the acting staff member's own account, and a business
 * must always retain at least one active BUSINESS_ADMIN. Self-reset-via-admin-endpoint is
 * already covered by StaffPasswordManagementIntegrationTest for the business-scoped path;
 * this class adds the platform-admin-scoped counterpart plus every deactivate scenario.
 */
class StaffSelfManagementIntegrationTest extends AbstractIntegrationTest {

    @Test
    void businessAdminCannotDeactivateTheirOwnAccountThroughTheAdminDeactivateEndpoint() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Self Deactivate Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminAEmail = "self-deactivate-admin-a@example.com";
        MockCookie adminACookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, adminAEmail));

        // A second active BUSINESS_ADMIN exists so this test isolates the self-check from the
        // separate last-active-BUSINESS_ADMIN guard (both would otherwise reject the same call).
        mockMvc.perform(post("/api/staff/staff-users")
                        .cookie(adminACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"self-deactivate-admin-b@example.com\",\"password\":\""
                                + StaffFixtures.DEFAULT_PASSWORD + "\",\"role\":\"BUSINESS_ADMIN\",\"branchIds\":[\""
                                + branchId + "\"]}"))
                .andExpect(status().isCreated());

        String adminAId = objectMapper
                .readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(adminACookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("staffUserId")
                .asText();

        mockMvc.perform(post("/api/staff/staff-users/{staffUserId}/deactivate", adminAId).cookie(adminACookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + adminAEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void businessAdminCanStillDeactivateAnotherStaffMemberInTheirBranch() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Other Staff Deactivate Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "other-staff-admin@example.com"));

        String cashierEmail = "other-staff-cashier@example.com";
        String cashierId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/staff-users")
                                .cookie(adminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/staff/staff-users/{staffUserId}/deactivate", cashierId).cookie(adminCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformAdminCannotDeactivateTheLastActiveBusinessAdminOfABusiness() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Last Admin Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String targetAdminEmail = "last-admin-target@example.com";
        StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, targetAdminEmail);
        String platformAdminBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Last Admin PA Home Business");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, platformAdminBusinessId, "last-admin-pa@example.com"));

        String targetAdminId = objectMapper
                .readTree(mockMvc.perform(get("/api/platform-admin/businesses/{businessId}/staff-users", businessId)
                                .cookie(platformAdminCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0)
                .get("id")
                .asText();

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/deactivate",
                                businessId,
                                targetAdminId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + targetAdminEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void platformAdminCanDeactivateABusinessAdminWhenAnotherActiveBusinessAdminRemains() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Not Last Admin Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String firstAdminEmail = "not-last-admin-a@example.com";
        MockCookie firstAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, firstAdminEmail));
        String secondAdminEmail = "not-last-admin-b@example.com";
        mockMvc.perform(post("/api/staff/staff-users")
                        .cookie(firstAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + secondAdminEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BUSINESS_ADMIN\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());

        String platformAdminBusinessId =
                TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Not Last Admin PA Home Business");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, platformAdminBusinessId, "not-last-admin-pa@example.com"));

        String firstAdminId = objectMapper
                .readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(firstAdminCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("staffUserId")
                .asText();

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/deactivate",
                                businessId,
                                firstAdminId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + firstAdminEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + secondAdminEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    /** target.id == actor.id always implies target.role == PLATFORM_ADMIN here (only PLATFORM_ADMIN
     * can reach this endpoint), which is already rejected by requireNonPlatformAdminTarget - covered
     * explicitly since "cannot deactivate your own account" is the property that actually matters,
     * mirroring PlatformAdminStaffHardDeleteIntegrationTest.platformAdminCannotHardDeleteItsOwnAccount. */
    @Test
    void platformAdminCannotDeactivateItsOwnAccount() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "PA Self Deactivate Business");
        String email = "pa-self-deactivate@example.com";
        MockCookie platformAdminCookie =
                new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, email));

        String ownStaffUserId = objectMapper
                .readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(platformAdminCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("staffUserId")
                .asText();

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/deactivate",
                                businessId,
                                ownStaffUserId)
                        .cookie(platformAdminCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    /** Same reasoning as platformAdminCannotDeactivateItsOwnAccount: requireNonPlatformAdminTarget
     * already rejects a self-targeted reset since the caller's own row is always PLATFORM_ADMIN. */
    @Test
    void platformAdminCannotResetItsOwnPasswordThroughTheAdminResetEndpoint() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "PA Self Reset Business");
        String email = "pa-self-reset@example.com";
        MockCookie platformAdminCookie =
                new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, email));

        String ownStaffUserId = objectMapper
                .readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(platformAdminCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("staffUserId")
                .asText();

        mockMvc.perform(post(
                                "/api/platform-admin/businesses/{businessId}/staff-users/{staffUserId}/reset-password",
                                businessId,
                                ownStaffUserId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"brand-new-password-1\",\"confirmNewPassword\":\"brand-new-password-1\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }
}
