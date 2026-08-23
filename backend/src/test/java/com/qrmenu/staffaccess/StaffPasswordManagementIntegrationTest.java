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
 * Self-service password change (POST /api/staff/auth/change-password) and admin-triggered
 * reset (POST /api/staff/staff-users/{id}/reset-password): correct-current-password success,
 * wrong-current-password rejection, mismatched confirmation, unauthorized/cross-branch reset,
 * self-reset-through-the-admin-endpoint rejection, and that a reset never re-enables a
 * disabled account's login.
 */
class StaffPasswordManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String NEW_PASSWORD = "brand-new-password-1";

    @Test
    void selfServiceChangeWithCorrectCurrentPasswordSucceedsAndOldPasswordNoLongerWorks() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Change Password Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String email = "change-pw-admin@example.com";
        String cookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, email);
        MockCookie mockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie);

        mockMvc.perform(post("/api/staff/auth/change-password")
                        .cookie(mockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\",\"newPassword\":\""
                                + NEW_PASSWORD + "\",\"confirmNewPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    /** The session used to make the change survives; every other session of the same user does not. */
    @Test
    void selfServiceChangeRevokesOtherSessionsButKeepsTheOneThatMadeTheChange() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Session Revoke Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String email = "session-revoke-admin@example.com";
        String firstCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, email);
        String secondCookie = StaffFixtures.login(mockMvc, email);
        MockCookie firstMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, firstCookie);
        MockCookie secondMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, secondCookie);

        mockMvc.perform(post("/api/staff/auth/change-password")
                        .cookie(firstMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\",\"newPassword\":\""
                                + NEW_PASSWORD + "\",\"confirmNewPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/staff/auth/me").cookie(firstMockCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/staff/auth/me").cookie(secondMockCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void selfServiceChangeWithWrongCurrentPasswordIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Wrong Current Password Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String email = "wrong-current-admin@example.com";
        String cookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, email);

        mockMvc.perform(post("/api/staff/auth/change-password")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"totally-wrong\",\"newPassword\":\"" + NEW_PASSWORD
                                + "\",\"confirmNewPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void selfServiceChangeWithMismatchedConfirmationIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Mismatch Confirm Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "mismatch-confirm-admin@example.com");

        mockMvc.perform(post("/api/staff/auth/change-password")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmNewPassword\":\"does-not-match\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessAdminCanResetAStaffMembersPasswordInTheirOwnBranchAndTheStaffMembersOtherSessionsAreRevoked() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Reset Success Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "reset-success-admin@example.com");
        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String cashierEmail = "reset-success-cashier@example.com";
        String cashierId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/staff-users")
                                .cookie(adminMockCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
        MockCookie cashierMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, cashierEmail));

        mockMvc.perform(post("/api/staff/staff-users/{staffUserId}/reset-password", cashierId)
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmNewPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        // The cashier's pre-reset session is dead - a reset revokes every session of the target.
        mockMvc.perform(get("/api/staff/auth/me").cookie(cashierMockCookie)).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aStaffMemberWithoutStaffManageCannotResetAnyonesPassword() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Reset Unauthorized Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "reset-unauthorized-admin@example.com");
        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String cashierEmail = "reset-unauthorized-cashier@example.com";
        String cashierId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/staff-users")
                                .cookie(adminMockCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
        MockCookie cashierMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, cashierEmail));

        // CASHIER has no STAFF_MANAGE - cannot reset even their own colleague's password.
        mockMvc.perform(post("/api/staff/staff-users/{staffUserId}/reset-password", cashierId)
                        .cookie(cashierMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmNewPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessAdminCannotResetTheirOwnPasswordThroughTheAdminResetEndpoint() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Self Reset Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "self-reset-admin@example.com");
        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String adminId = objectMapper
                .readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(adminMockCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("staffUserId")
                .asText();

        mockMvc.perform(post("/api/staff/staff-users/{staffUserId}/reset-password", adminId)
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmNewPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessAdminCannotResetAStaffMembersPasswordInAnotherBranch() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cross Branch Reset Business");
        String branchAId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Branch A");
        String branchBId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Branch B");
        String adminACookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchAId, "cross-branch-admin-a@example.com");
        String adminBCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchBId, "cross-branch-admin-b@example.com");
        MockCookie adminAMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminACookie);
        MockCookie adminBMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminBCookie);

        String branchBCashierEmail = "cross-branch-cashier@example.com";
        String branchBCashierId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/staff-users")
                                .cookie(adminBMockCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + branchBCashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchBId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        // Branch A's admin cannot reset a staff member scoped to Branch B, even within the same business.
        mockMvc.perform(post("/api/staff/staff-users/{staffUserId}/reset-password", branchBCashierId)
                        .cookie(adminAMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmNewPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resettingADisabledStaffMembersPasswordDoesNotReenableTheirLogin() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Disabled Reset Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "disabled-reset-admin@example.com");
        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String cashierEmail = "disabled-reset-cashier@example.com";
        String cashierId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/staff-users")
                                .cookie(adminMockCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                        + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
        mockMvc.perform(post("/api/staff/staff-users/{staffUserId}/deactivate", cashierId).cookie(adminMockCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/staff/staff-users/{staffUserId}/reset-password", cashierId)
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\",\"confirmNewPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
