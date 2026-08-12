package com.qrmenu.staffaccess;

import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #12: StaffContext.canAccessBranch() let BUSINESS_ADMIN reach ANY
 * branchId, not just its own business's - KitchenController/OrderControlController/
 * RefundController all resolve authorization through
 * StaffAuthService.resolveStaffContextForBranch alone, with no secondary businessId
 * check downstream, so fixing that one choke point closes the hole for all three.
 */
class CrossTenantBranchAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void businessAdminCannotReachAnotherBusinesssBranchScopedEndpoints() throws Exception {
        String businessA = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cross-Tenant A");
        String businessB = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cross-Tenant B");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessB, "B Şube");
        String adminACookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessA, "cross-tenant-admin-a@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminACookie);

        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders", branchB).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchB).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders/search", branchB).param("orderNumber", "1").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
