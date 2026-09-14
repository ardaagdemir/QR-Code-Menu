package com.qrmenu.staffaccess;

import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gap-analysis #16: user-facing staff roles are isolated to one session-resolved branch. */
class CrossTenantBranchAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void businessAdminCannotSelectAnotherBranchAndCurrentTablesNeverLeakIt() throws Exception {
        BranchFixture fixture = createTwoBranchFixture("isolation-admin");
        String tableA = TenantFixtures.createTable(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchA(), "A Masa");
        String tableB = TenantFixtures.createTable(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchB(), "B Gizli Masa");
        String qrTokenB = objectMapper.readTree(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/internal/businesses/{businessId}/tables/{tableId}/qr-tokens", fixture.businessId(), tableB)
                                .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asText();
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchA(),
                "isolation-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(get("/api/staff/tables").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(tableA))
                .andExpect(jsonPath("$[0].label").value("A Masa"));
        assertLegacyBranchSelectionDenied(cookie, fixture.branchB());
        mockMvc.perform(get("/api/staff/branches/{branchId}/tables", fixture.branchB()).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/branches/{branchId}/reports", fixture.branchB())
                        .param("from", "2026-08-01").param("to", "2026-08-14").cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/tables/{tableId}/qr-tokens/active", tableB).cookie(cookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/staff/tables/{tableId}/qr-tokens", tableB).cookie(cookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/staff/qr-tokens/{qrTokenId}/revoke", qrTokenB).cookie(cookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/staff/tables/{tableId}", tableB)
                        .cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Ele Geçirilmiş Masa\",\"location\":\"INDOOR\"}"))
                .andExpect(status().isNotFound());
    }

    /** True cross-tenant (different businessId, not just a second branch of the same business):
     * a real branchId/tableId from another business must still be rejected, since a session's
     * StaffContext.businessId() is what every branchId path/query param gets cross-checked
     * against (StaffAuthService.resolveStaffContextForBranch). */
    @Test
    void businessAdminCannotReachAnotherBusinesssBranchEvenWithItsRealBranchId() throws Exception {
        String businessAId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "tenant-a");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "A Şube");
        String businessBId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "tenant-b");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, "B Şube");
        String tableB = TenantFixtures.createTable(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, branchB, "B Başka İşletme Masası");
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessAId, branchA,
                "tenant-a-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(get("/api/staff/branches/{branchId}/tables", branchB).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchB).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/branches/{branchId}/reports", branchB)
                        .param("from", "2026-08-01").param("to", "2026-08-14").cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/staff/tables/{tableId}", tableB)
                        .cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Ele Geçirilmiş Masa\",\"location\":\"INDOOR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void businessAdminCanRenameATableInTheirOwnBranchAndTheChangePersists() throws Exception {
        BranchFixture fixture = createTwoBranchFixture("rename-admin");
        String tableId = TenantFixtures.createTable(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchA(), "Eski Ad");
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchA(),
                "rename-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/staff/tables/{tableId}", tableId)
                        .cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Yeni Ad\",\"location\":\"OUTDOOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Yeni Ad"));

        mockMvc.perform(get("/api/staff/tables").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Yeni Ad"));
    }

    @Test
    void branchManagerCannotReachAnotherBranchOrders() throws Exception {
        BranchFixture fixture = createTwoBranchFixture("isolation-manager");
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchA(),
                "isolation-manager@example.com", "BRANCH_MANAGER"));

        mockMvc.perform(get("/api/staff/orders/pending-acceptance").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertLegacyBranchSelectionDenied(cookie, fixture.branchB());
    }

    @Test
    void cashierCannotReachAnotherBranchOrders() throws Exception {
        BranchFixture fixture = createTwoBranchFixture("isolation-cashier");
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchA(),
                "isolation-cashier@example.com", "CASHIER"));

        mockMvc.perform(get("/api/staff/orders/pending-acceptance").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertLegacyBranchSelectionDenied(cookie, fixture.branchB());
    }

    private void assertLegacyBranchSelectionDenied(MockCookie cookie, String otherBranchId) throws Exception {
        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/in-progress", otherBranchId).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/pending-acceptance", otherBranchId).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/search", otherBranchId)
                        .param("orderNumber", "1").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    private BranchFixture createTwoBranchFixture(String prefix) throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, prefix);
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "A Şube");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "B Şube");
        return new BranchFixture(businessId, branchA, branchB);
    }

    private static MockCookie cookie(String value) {
        return new MockCookie(StaffCookieSupport.COOKIE_NAME, value);
    }

    private record BranchFixture(String businessId, String branchA, String branchB) {
    }
}
