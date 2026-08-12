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
 * (Permission-not-held -> 403, branch scoping for BRANCH_MANAGER/KITCHEN_STAFF -> 403
 * outside their assigned branches), and that mutations through the new /api/staff/**
 * admin endpoints show up in the audit log.
 */
class StaffAccessFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void loginSucceedsMeReflectsContextAndLogoutInvalidatesTheSession() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Auth Business");
        String email = "auth-admin-1@example.com";
        String cookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, email);

        mockMvc.perform(get("/api/staff/auth/me").cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("BUSINESS_ADMIN"))
                .andExpect(jsonPath("$.businessName").value("Auth Business"))
                .andExpect(jsonPath("$.branches").isEmpty());

        mockMvc.perform(post("/api/staff/auth/logout").cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/staff/auth/me").cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Wrong Password Business");
        String email = "auth-admin-2@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD + "\",\"role\":\"BUSINESS_ADMIN\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/staff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aPermissionTheRoleDoesNotHoldIsForbidden() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Permission Business");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, "permission-admin@example.com");

        String kitchenStaffEmail = "kitchen-staff@example.com";
        mockMvc.perform(post("/api/staff/staff-users")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + kitchenStaffEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"KITCHEN_STAFF\"}"))
                .andExpect(status().isCreated());
        String kitchenStaffCookie = StaffFixtures.login(mockMvc, kitchenStaffEmail);

        // KITCHEN_STAFF only holds Permission.KITCHEN_DECIDE - BRANCH_MANAGE is out of reach.
        mockMvc.perform(get("/api/staff/branches").cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, kitchenStaffCookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void branchScopedPermissionsAreDeniedOutsideTheStaffUsersAssignedBranches() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Branch Scope Business");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, "scope-admin@example.com");

        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        MvcResult branchAResult = mockMvc.perform(post("/api/staff/branches")
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Branch A\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String branchAId = objectMapper.readTree(branchAResult.getResponse().getContentAsString()).get("id").asText();

        MvcResult branchBResult = mockMvc.perform(post("/api/staff/branches")
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Branch B\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String branchBId = objectMapper.readTree(branchBResult.getResponse().getContentAsString()).get("id").asText();

        String kitchenStaffEmail = "scoped-kitchen-staff@example.com";
        mockMvc.perform(post("/api/staff/staff-users")
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + kitchenStaffEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"KITCHEN_STAFF\",\"branchIds\":[\"" + branchAId + "\"]}"))
                .andExpect(status().isCreated());
        String kitchenStaffCookie = StaffFixtures.login(mockMvc, kitchenStaffEmail);
        MockCookie kitchenMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, kitchenStaffCookie);

        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders", branchAId).cookie(kitchenMockCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders", branchBId).cookie(kitchenMockCookie))
                .andExpect(status().isForbidden());

        // /me's branches list (used by staff-web's AppShell top bar context, Section 19.3) only
        // reflects the staff user's own assigned branches, not every branch in the business.
        mockMvc.perform(get("/api/staff/auth/me").cookie(kitchenMockCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branches", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.branches[0].id").value(branchAId))
                .andExpect(jsonPath("$.branches[0].name").value("Branch A"));
    }

    @Test
    void mutationsThroughTheStaffAdminEndpointsAreRecordedInTheAuditLog() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Audit Business");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, "audit-admin@example.com");
        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String meBody = mockMvc.perform(get("/api/staff/auth/me").cookie(adminMockCookie))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String staffUserId = objectMapper.readTree(meBody).get("staffUserId").asText();

        mockMvc.perform(post("/api/staff/menu-categories")
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana Yemekler\"}"))
                .andExpect(status().isCreated());

        JsonNode entries = objectMapper.readTree(mockMvc.perform(get("/api/staff/audit").cookie(adminMockCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.get("entityType").asText()).isEqualTo("MenuCategory");
            assertThat(entry.get("action").asText()).isEqualTo("CREATED");
            assertThat(entry.get("actorStaffUserId").asText()).isEqualTo(staffUserId);
        });
    }

    @Test
    void staffCanTogglePassiveOnAnExistingProductAndTheChangePersists() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Product Toggle Business");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, "product-toggle-admin@example.com");
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
