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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Business-scoped staff lifecycle actions added alongside deactivate: reactivate, role
 * change, and email edit - plus the platform-admin counterparts that share the same
 * guards (self-management block, PLATFORM_ADMIN-target block, last-active-BUSINESS_ADMIN
 * protection, session invalidation). Branch isolation is exercised by creating the
 * out-of-branch target through the platform-admin panel, which is the only entry point
 * that can assign a staff user to a branch other than the acting BUSINESS_ADMIN's own
 * (the business-scoped create endpoint always assigns the caller's own active branch -
 * see StaffUserController.create).
 */
class StaffLifecycleManagementIntegrationTest extends AbstractIntegrationTest {

    private String cashierId(MockCookie adminCookie, String email, String businessId, String branchId) throws Exception {
        return objectMapper
                .readTree(mockMvc.perform(post("/api/staff/staff-users")
                                .cookie(adminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
    }

    private String staffUserIdOf(MockCookie cookie) throws Exception {
        return objectMapper
                .readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(cookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("staffUserId")
                .asText();
    }

    // -- Reactivate ----------------------------------------------------------------------

    @Test
    void businessAdminCanReactivateADeactivatedStaffMemberWithoutChangingTheirPassword() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Reactivate Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "reactivate-admin@example.com"));
        String cashierEmail = "reactivate-cashier@example.com";
        String cashierId = cashierId(adminCookie, cashierEmail, businessId, branchId);

        mockMvc.perform(post("/api/staff/staff-users/{id}/deactivate", cashierId).cookie(adminCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/staff/staff-users/{id}/activate", cashierId).cookie(adminCookie))
                .andExpect(status().isNoContent());

        // Reactivate never touches the password hash - the original password still works.
        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deactivateInvalidatesTheTargetsExistingSessionAndReactivateDoesNotReviveIt() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "No Session Revival Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "no-revival-admin@example.com"));
        String cashierEmail = "no-revival-cashier@example.com";
        String cashierId = cashierId(adminCookie, cashierEmail, businessId, branchId);
        MockCookie cashierCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, cashierEmail));

        // Sanity: the cashier's pre-deactivation session is live.
        mockMvc.perform(get("/api/staff/auth/me").cookie(cashierCookie)).andExpect(status().isOk());

        mockMvc.perform(post("/api/staff/staff-users/{id}/deactivate", cashierId).cookie(adminCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/staff/auth/me").cookie(cashierCookie)).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/staff/staff-users/{id}/activate", cashierId).cookie(adminCookie))
                .andExpect(status().isNoContent());

        // The old, pre-deactivation session cookie must stay dead even after reactivation.
        mockMvc.perform(get("/api/staff/auth/me").cookie(cashierCookie)).andExpect(status().isUnauthorized());

        // A fresh login (new session) works fine.
        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void businessAdminCannotActivateTheirOwnAccount() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Self Activate Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "self-activate-admin@example.com"));
        String adminId = staffUserIdOf(adminCookie);

        mockMvc.perform(post("/api/staff/staff-users/{id}/activate", adminId).cookie(adminCookie))
                .andExpect(status().isForbidden());
    }

    // -- Role change -----------------------------------------------------------------------

    @Test
    void businessAdminCanChangeAnotherStaffMembersRoleAndItInvalidatesTheirSessions() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Role Change Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "role-change-admin@example.com"));
        String cashierEmail = "role-change-cashier@example.com";
        String cashierId = cashierId(adminCookie, cashierEmail, businessId, branchId);
        MockCookie cashierCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, cashierEmail));

        mockMvc.perform(post("/api/staff/staff-users/{id}/role", cashierId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"BRANCH_MANAGER\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/staff/staff-users").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + cashierId + "')].role").value("BRANCH_MANAGER"));

        // Old session, minted under the CASHIER role, no longer works - the target must
        // re-login to pick up the new role's permissions.
        mockMvc.perform(get("/api/staff/auth/me").cookie(cashierCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void businessAdminCannotChangeTheirOwnRole() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Self Role Change Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "self-role-admin@example.com"));
        String adminId = staffUserIdOf(adminCookie);

        mockMvc.perform(post("/api/staff/staff-users/{id}/role", adminId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CASHIER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessAdminCannotPromoteStaffToPlatformAdmin() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "No Promote PA Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "no-promote-admin@example.com"));
        String cashierId = cashierId(adminCookie, "no-promote-cashier@example.com", businessId, branchId);

        mockMvc.perform(post("/api/staff/staff-users/{id}/role", cashierId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PLATFORM_ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    /** The business-scoped role-change endpoint can never itself trigger the last-active-
     * BUSINESS_ADMIN guard (STAFF_MANAGE is BUSINESS_ADMIN-only and self-change is blocked, so
     * the acting BUSINESS_ADMIN always counts as "another" admin remaining) - the guard is
     * exercised for real through the platform-admin panel below, where the actor is a
     * PLATFORM_ADMIN and doesn't offset the count. */
    @Test
    void platformAdminCannotDemoteTheLastActiveBusinessAdminsRole() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Last Admin Role Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String targetAdminEmail = "last-admin-role-target@example.com";
        StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, targetAdminEmail);
        String platformAdminBusinessId =
                TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Last Admin Role PA Home Business");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, platformAdminBusinessId, "last-admin-role-pa@example.com"));
        String targetAdminId = objectMapper
                .readTree(mockMvc.perform(get("/api/platform-admin/businesses/{businessId}/staff-users", businessId)
                                .cookie(platformAdminCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0)
                .get("id")
                .asText();

        mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/staff-users/{id}/role", businessId, targetAdminId)
                        .cookie(platformAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CASHIER\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + targetAdminEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    // -- Email edit --------------------------------------------------------------------------

    @Test
    void businessAdminCanEditStaffEmailAndItIsNormalizedAndInvalidatesTheirSessions() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Email Edit Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "email-edit-admin@example.com"));
        String originalEmail = "email-edit-cashier@example.com";
        String cashierId = cashierId(adminCookie, originalEmail, businessId, branchId);
        MockCookie cashierCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, originalEmail));

        mockMvc.perform(post("/api/staff/staff-users/{id}/email", cashierId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  Fixed-Email@Example.com  \"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/staff/staff-users").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + cashierId + "')].email").value("fixed-email@example.com"));

        // Old session invalidated - must re-login to pick up the corrected identity.
        mockMvc.perform(get("/api/staff/auth/me").cookie(cashierCookie)).andExpect(status().isUnauthorized());

        // Login also normalizes, so any casing of the corrected address works.
        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Fixed-Email@Example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void businessAdminCannotEditTheirOwnEmail() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Self Email Edit Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "self-email-admin@example.com"));
        String adminId = staffUserIdOf(adminCookie);

        mockMvc.perform(post("/api/staff/staff-users/{id}/email", adminId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone-else@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editingEmailToOneAlreadyInUseIsRejectedCaseInsensitivelyWithAConflict() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Duplicate Email Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "duplicate-email-admin@example.com"));
        cashierId(adminCookie, "taken@example.com", businessId, branchId);
        String otherCashierId = cashierId(adminCookie, "duplicate-email-cashier@example.com", businessId, branchId);

        mockMvc.perform(post("/api/staff/staff-users/{id}/email", otherCashierId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"TAKEN@example.com\"}"))
                .andExpect(status().isConflict());
    }

    // -- PLATFORM_ADMIN target / branch isolation ---------------------------------------------

    @Test
    void businessScopedActionsCannotTargetAPlatformAdmin() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "No PA Target Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "no-pa-target-admin@example.com"));
        // PLATFORM_ADMIN is filtered out of GET /api/staff/staff-users (see
        // StaffAuthService.listStaffUsers(businessId, branchId)), so its id is fetched directly
        // via the internal bootstrap + login flow instead.
        StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "no-pa-target-pa@example.com");
        MockCookie paCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, "no-pa-target-pa@example.com"));
        String platformAdminId = staffUserIdOf(paCookie);

        mockMvc.perform(post("/api/staff/staff-users/{id}/deactivate", platformAdminId).cookie(adminCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/staff-users/{id}/activate", platformAdminId).cookie(adminCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/staff-users/{id}/role", platformAdminId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CASHIER\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/staff-users/{id}/email", platformAdminId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hijacked@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessAdminCannotManageStaffAssignedToAnotherBranch() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Branch Isolation Business");
        String branch1 = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube 1");
        String branch2 = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube 2");
        MockCookie adminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branch1, "branch-isolation-admin@example.com"));

        // Only the platform-admin create endpoint honors a caller-chosen branch - business-scoped
        // create always assigns the caller's own active branch (StaffUserController.create).
        String platformAdminBusinessId =
                TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Branch Isolation PA Home Business");
        MockCookie platformAdminCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, platformAdminBusinessId, "branch-isolation-pa@example.com"));
        String otherBranchCashierEmail = "branch-isolation-cashier@example.com";
        String otherBranchCashierId = objectMapper
                .readTree(mockMvc.perform(post("/api/platform-admin/businesses/{businessId}/staff-users", businessId)
                                .cookie(platformAdminCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + otherBranchCashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branch2 + "\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/staff/staff-users/{id}/deactivate", otherBranchCashierId).cookie(adminCookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/staff-users/{id}/activate", otherBranchCashierId).cookie(adminCookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/staff-users/{id}/role", otherBranchCashierId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"BRANCH_MANAGER\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/staff-users/{id}/email", otherBranchCashierId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other-branch-new@example.com\"}"))
                .andExpect(status().isNotFound());
    }
}
