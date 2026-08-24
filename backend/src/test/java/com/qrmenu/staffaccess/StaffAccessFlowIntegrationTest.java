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
                .andExpect(jsonPath("$.activeBranchId").value(branchId))
                // No explicit Branch.timezone was set - falls back to Business.defaultTimeZone
                // (TenantFixtures.createBusiness leaves it at "Europe/Istanbul"), never UTC.
                .andExpect(jsonPath("$.activeBranchTimeZone").value("Europe/Istanbul"));

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

    /** Privilege escalation: STAFF_MANAGE (held by BUSINESS_ADMIN) must not double as authority
     * to grant the all-permissions PLATFORM_ADMIN role - only the trusted /internal/** bootstrap
     * path may create one. */
    @Test
    void businessAdminCannotGrantThemselvesOrAnotherStaffMemberThePlatformAdminRole() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Escalation Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "escalation-admin@example.com", "BUSINESS_ADMIN");
        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String escalatedEmail = "escalated@example.com";
        mockMvc.perform(post("/api/staff/staff-users")
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + escalatedEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"PLATFORM_ADMIN\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isForbidden());

        JsonNode staffUsers = objectMapper.readTree(mockMvc.perform(get("/api/staff/staff-users").cookie(adminMockCookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(staffUsers).noneSatisfy(staffUser -> assertThat(staffUser.get("email").asText()).isEqualTo(escalatedEmail));
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

    /** Edit must support the same field set as create (name/description/price/KDV), not just active/prep/allergens/image. */
    @Test
    void staffCanEditEveryFieldOfAnExistingProductAndTheChangesPersist() throws Exception {
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
                        .content("{\"name\":\"Köfte Deluxe\",\"description\":\"Özel soslu\",\"basePriceMinorUnits\":15000,"
                                + "\"taxRatePercent\":20,\"active\":false,\"estimatedPreparationMinutes\":20,"
                                + "\"allergens\":[\"GLUTEN\"]}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode updated = objectMapper.readTree(response);
        assertThat(updated.get("name").asText()).isEqualTo("Köfte Deluxe");
        assertThat(updated.get("description").asText()).isEqualTo("Özel soslu");
        assertThat(updated.get("basePriceMinorUnits").asLong()).isEqualTo(15000);
        assertThat(updated.get("taxRatePercent").asInt()).isEqualTo(20);
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
                assertThat(product.get("name").asText()).isEqualTo("Köfte Deluxe");
                assertThat(product.get("basePriceMinorUnits").asLong()).isEqualTo(15000);
                assertThat(product.get("active").asBoolean()).isFalse();
            }
        });
    }

    /** Section 2/12: a branch-scoped role (no MENU_MANAGE) must not be able to touch global
     * Product fields or the branch's BranchProduct opt-in row either - both go through the
     * same permission gate today, so neither should be reachable without it. */
    @Test
    void branchScopedStaffCannotMutateGlobalOrBranchProductState() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Product Guard Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "product-guard-admin@example.com", "BUSINESS_ADMIN");
        MockCookie adminMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId =
                TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Ana Yemekler");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Çorba", 8000, 10);

        String cashierEmail = "product-guard-cashier@example.com";
        mockMvc.perform(post("/api/staff/staff-users")
                        .cookie(adminMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        MockCookie cashierMockCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, cashierEmail));

        mockMvc.perform(post("/api/staff/products")
                        .cookie(cashierMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"name\":\"Kaçak Ürün\",\"basePriceMinorUnits\":1000,"
                                + "\"taxRatePercent\":10}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/staff/products/{productId}", productId)
                        .cookie(cashierMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ele Geçirilmiş\",\"basePriceMinorUnits\":1,\"taxRatePercent\":0,"
                                + "\"active\":false,\"estimatedPreparationMinutes\":null,\"allergens\":[]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/staff/branch-products/{productId}", productId)
                        .cookie(cashierMockCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availability\":\"UNAVAILABLE\",\"priceOverrideMinorUnits\":null}"))
                .andExpect(status().isForbidden());

        JsonNode untouched = objectMapper.readTree(mockMvc.perform(get(
                                "/api/staff/menu-categories/{categoryId}/products", categoryId)
                        .cookie(adminMockCookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(untouched).anySatisfy(product -> {
            if (product.get("id").asText().equals(productId)) {
                assertThat(product.get("name").asText()).isEqualTo("Çorba");
                assertThat(product.get("basePriceMinorUnits").asLong()).isEqualTo(8000);
            }
        });
    }

    /** PLATFORM_ADMIN is auto-assigned to every branch of its home business (createStaffUser), so
     * without an explicit filter it would otherwise show up as a regular team member in that
     * business's own Personel screen - and without an explicit role check, a BUSINESS_ADMIN who
     * learned its staffUserId could actually deactivate or reset the password of that PLATFORM_ADMIN
     * account. Both must be fully blocked: invisible in the list, and rejected on every mutation. */
    @Test
    void businessScopedStaffListAndMutationsNeverExposeAPlatformAdminSharingTheBusiness() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Platform Admin Hiding Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        MockCookie adminCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "hiding-admin@example.com", "BUSINESS_ADMIN"));

        String platformAdminEmail = "hidden-platform-admin@example.com";
        MockCookie platformAdminCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, platformAdminEmail));
        String platformAdminStaffId = objectMapper.readTree(mockMvc.perform(get("/api/staff/auth/me").cookie(platformAdminCookie))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("staffUserId").asText();

        JsonNode staffUsers = objectMapper.readTree(mockMvc.perform(get("/api/staff/staff-users").cookie(adminCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(staffUsers).hasSize(1);
        assertThat(staffUsers).noneSatisfy(staffUser -> assertThat(staffUser.get("role").asText()).isEqualTo("PLATFORM_ADMIN"));
        assertThat(staffUsers).noneSatisfy(staffUser -> assertThat(staffUser.get("email").asText()).isEqualTo(platformAdminEmail));

        mockMvc.perform(post("/api/staff/staff-users/{id}/deactivate", platformAdminStaffId).cookie(adminCookie))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/staff/staff-users/{id}/reset-password", platformAdminStaffId)
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"attacker-chosen-1234\",\"confirmNewPassword\":\"attacker-chosen-1234\"}"))
                .andExpect(status().is4xxClientError());

        // The PLATFORM_ADMIN account itself must be untouched by the rejected attempts.
        mockMvc.perform(get("/api/staff/auth/me").cookie(platformAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLATFORM_ADMIN"));
    }
}
