package com.qrmenu.support;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bootstraps a StaffUser through the real internal admin-token API and logs in through
 * the real /api/staff/auth/login endpoint, mirroring TenantFixtures.checkIn's approach
 * of extracting the Set-Cookie header value directly rather than touching repositories
 * (Milestone 8: replaces the deleted X-Staff-Access-Token shared-secret test fixture).
 */
public final class StaffFixtures {

    private StaffFixtures() {
    }

    public static final String DEFAULT_PASSWORD = "test-password-1234";

    /** Bootstraps a BUSINESS_ADMIN for the business - has every business-scoped permission. */
    public static String bootstrapBusinessAdminAndLogin(
            MockMvc mockMvc, String adminToken, String businessId, String branchId, String email)
            throws Exception {
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DEFAULT_PASSWORD
                                + "\",\"role\":\"BUSINESS_ADMIN\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        return login(mockMvc, email);
    }

    /** Bootstraps a PLATFORM_ADMIN through the /internal/** shared-token API - the only place
     * a PLATFORM_ADMIN is ever created (see platformadmin.web tests: the /api/platform-admin/**
     * panel deliberately cannot mint or promote to this role). branchIds is ignored server-side
     * (StaffAuthService.createStaffUser auto-assigns every branch of businessId to PLATFORM_ADMIN). */
    public static String bootstrapPlatformAdminAndLogin(MockMvc mockMvc, String adminToken, String businessId, String email)
            throws Exception {
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DEFAULT_PASSWORD
                                + "\",\"role\":\"PLATFORM_ADMIN\",\"branchIds\":[]}"))
                .andExpect(status().isCreated());
        return login(mockMvc, email);
    }

    public static String bootstrapAndLogin(
            MockMvc mockMvc, String adminToken, String businessId, String branchId, String email, String role)
            throws Exception {
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DEFAULT_PASSWORD
                                + "\",\"role\":\"" + role + "\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        return login(mockMvc, email);
    }

    /** Returns the qrmenu_staff_session cookie value from a successful login. */
    public static String login(MockMvc mockMvc, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        return setCookie.split(";")[0].split("=", 2)[1];
    }
}
