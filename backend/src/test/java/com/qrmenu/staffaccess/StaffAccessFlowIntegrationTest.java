package com.qrmenu.staffaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Milestone 8: real StaffUser login/logout/me, the permission layer
 * (Permission-not-held -> 403, branch scoping for BRANCH_MANAGER/CASHIER -> 403
 * outside their assigned branches), and that mutations through the new /api/staff/**
 * admin endpoints show up in the audit log.
 */
class StaffAccessFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void loginSucceedsMeReflectsContextAndLogoutInvalidatesTheSession() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Auth Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Auth Şube");
        String email = "auth-admin-1@example.com";
        String cookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, email);

        mockMvc.perform(get("/api/staff/auth/me").cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("BUSINESS_ADMIN"))
                .andExpect(jsonPath("$.businessName").value("Auth Business"))
                .andExpect(jsonPath("$.branches[0].id").value(branchId))
                .andExpect(jsonPath("$.activeBranchId").value(branchId));

        mockMvc.perform(post("/api/staff/auth/logout").cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/staff/auth/me").cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Wrong Password Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String email = "auth-admin-2@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BUSINESS_ADMIN\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userFacingStaffCreationWithoutAnExplicitBranchIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Missing Branch Business");
        TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Tek Şube");

        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing-branch@example.com\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BUSINESS_ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPermissionTheRoleDoesNotHoldIsForbidden() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Permission Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "permission-admin@example.com", "BUSINESS_ADMIN");

        String cashierEmail = "boundary-cashier@example.com";
        mockMvc.perform(post("/api/staff/staff-users")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\"}"))
                .andExpect(status().isCreated());
        String cashierCookie = StaffFixtures.login(mockMvc, cashierEmail);

        // CASHIER holds ORDER_*/REPORT_VIEW - BRANCH_MANAGE is out of reach.
        mockMvc.perform(get("/api/staff/branches").cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void branchScopedPermissionsAreDeniedOutsideTheStaffUsersAssignedBranches() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Branch Scope Business");
        String branchAId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Branch A");
        String branchBId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Branch B");
        String adminCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchAId, "scope-admin@example.com", "BUSINESS_ADMIN");
        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String cashierEmail = "scoped-cashier@example.com";
        mockMvc.perform(post("/api/staff/staff-users")
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchAId + "\"]}"))
                .andExpect(status().isCreated());
        String cashierCookie = StaffFixtures.login(mockMvc, cashierEmail);
        MockCookie cashierMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie);

        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchAId).cookie(cashierMockCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchBId).cookie(cashierMockCookie))
                .andExpect(status().isForbidden());

        // /me's branches list (used by staff-web's AppShell top bar context, Section 19.3) only
        // reflects the staff user's own assigned branches, not every branch in the business.
        mockMvc.perform(get("/api/staff/auth/me").cookie(cashierMockCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.branches[0].id").value(branchAId))
                .andExpect(jsonPath("$.branches[0].name").value("Branch A"));
    }

    @Test
    void auditScreenReturnsOnlyTheActiveBranchsEntries() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Audit Business");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");
        MockCookie adminACookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchA, "audit-admin-a@example.com", "BUSINESS_ADMIN"));
        MockCookie adminBCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchB, "audit-admin-b@example.com", "BUSINESS_ADMIN"));

        String staffAId = objectMapper.readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(adminACookie))
                .andReturn().getResponse().getContentAsString()).get("staffUserId").asText();
        String staffBId = objectMapper.readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(adminBCookie))
                .andReturn().getResponse().getContentAsString()).get("staffUserId").asText();

        mockMvc.perform(post("/api/staff/menu-categories")
                        .cookie(adminACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Şube A Menüsü\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/staff/menu-categories")
                        .cookie(adminBCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Şube B Menüsü\"}"))
                .andExpect(status().isCreated());

        JsonNode entries = objectMapper.readTree(mockMvc.perform(get("/api/staff/audit").cookie(adminACookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.get("entityType").asText()).isEqualTo("MenuCategory");
            assertThat(entry.get("action").asText()).isEqualTo("CREATED");
            assertThat(entry.get("actorStaffUserId").asText()).isEqualTo(staffAId);
        });
        assertThat(entries).noneSatisfy(entry ->
                assertThat(entry.get("actorStaffUserId").asText()).isEqualTo(staffBId));
    }

    @Test
    void staffCanTogglePassiveOnAnExistingProductAndTheChangePersists() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Product Toggle Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "product-toggle-admin@example.com", "BUSINESS_ADMIN");
        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId =
                TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Ana Yemekler");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Köfte", 12000, 10);

        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/staff/products/{productId}", productId)
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"estimatedPreparationMinutes\":20,\"allergens\":[\"GLUTEN\"]}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode updated = objectMapper.readTree(response);
        assertThat(updated.get("active").asBoolean()).isFalse();
        assertThat(updated.get("estimatedPreparationMinutes").asInt()).isEqualTo(20);
        assertThat(updated.get("allergens")).hasSize(1);

        JsonNode products = objectMapper.readTree(mockMvc.perform(get(
                                "/api/staff/menu-categories/{categoryId}/products", categoryId)
                        .cookie(adminMockCookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(products).anySatisfy(product -> {
            if (product.get("id").asText().equals(productId)) {
                assertThat(product.get("active").asBoolean()).isFalse();
            }
        });
    }
}
