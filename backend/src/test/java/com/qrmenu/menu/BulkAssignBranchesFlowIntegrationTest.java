package com.qrmenu.menu;

import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gap-analysis #16: menu assignment can target only the session's active branch. */
class BulkAssignBranchesFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void businessAdminCanAssignOnlyToActiveBranch() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Menu Isolation");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Menü");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", 1000);
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchA, "menu-isolation@example.com", "BUSINESS_ADMIN"));
        MockCookie platformCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchA, "menu-platform@example.com", "PLATFORM_ADMIN"));

        mockMvc.perform(post("/api/staff/products/{productId}/branch-assignments", productId)
                        .cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"ALL_BRANCHES\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/products/{productId}/branch-assignments", productId)
                        .cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"SELECTED_BRANCHES\",\"branchIds\":[\"" + branchB + "\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/products/{productId}/branch-assignments", productId)
                        .cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"SELECTED_BRANCHES\",\"branchIds\":[\"" + branchA + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchId").value(branchA));

        // PLATFORM_ADMIN is scoped out of every normal business-operation endpoint (see
        // PlatformAdminScopeIntegrationTest) - MENU_MANAGE included, so even its former
        // ALL_BRANCHES bulk-assign exemption is unreachable now.
        mockMvc.perform(post("/api/staff/products/{productId}/branch-assignments", productId)
                        .cookie(platformCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"ALL_BRANCHES\"}"))
                .andExpect(status().isForbidden());
    }
}
