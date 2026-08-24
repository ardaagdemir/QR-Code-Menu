package com.qrmenu.platformadmin;

import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLATFORM_ADMIN scope narrowing: the role must be restricted to /api/platform-admin/**
 * (business/branch/staff-user management) plus its own account (session, password, /me).
 * It must NOT be able to reach normal business operations (Kasa/orders, menu, tables,
 * expenses, reports, refunds, branch operational settings, business-scoped staff
 * management) even though those endpoints are all reachable from a session-authenticated
 * staff cookie - PLATFORM_ADMIN previously held every Permission (StaffRole.permissions()
 * used EnumSet.allOf(Permission.class)), which let it through every one of these gates
 * as a side effect. StaffRole.permissions() now returns EnumSet.noneOf(Permission.class)
 * for PLATFORM_ADMIN instead, so every Permission-gated endpoint below must reject it -
 * the /api/platform-admin/** panel itself never checked a Permission (it role-checks
 * PLATFORM_ADMIN directly, see PlatformAdminBusinessController/PlatformAdminStaffController),
 * so it keeps working unchanged.
 */
class PlatformAdminScopeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void platformAdminCannotReachAnyNormalBusinessOperationEndpoint() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Scope Narrowing Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        MockCookie platformCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "scope-platform@example.com"));
        String today = LocalDate.now().toString();

        // Kasa / sipariş (ORDER_VIEW)
        mockMvc.perform(get("/api/staff/orders/pending-acceptance").cookie(platformCookie)).andExpect(status().isForbidden());

        // Refund (REFUND_ISSUE)
        mockMvc.perform(get("/api/staff/orders/search").param("orderNumber", "1").cookie(platformCookie))
                .andExpect(status().isForbidden());

        // Menü (MENU_MANAGE)
        mockMvc.perform(get("/api/staff/menu-categories").cookie(platformCookie)).andExpect(status().isForbidden());

        // Masalar (BRANCH_MANAGE)
        mockMvc.perform(get("/api/staff/tables").cookie(platformCookie)).andExpect(status().isForbidden());

        // Giderler (EXPENSE_VIEW)
        mockMvc.perform(get("/api/staff/expenses").param("from", today).param("to", today).cookie(platformCookie))
                .andExpect(status().isForbidden());

        // Raporlar (REPORT_VIEW / REPORT_CHAIN_VIEW / REPORT_FINANCIAL_SUMMARY_VIEW)
        mockMvc.perform(get("/api/staff/reports").param("from", today).param("to", today).cookie(platformCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/reports/chain").param("from", today).param("to", today).cookie(platformCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/reports/kitchen-summary").param("from", today).param("to", today).cookie(platformCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/branches/comparison").cookie(platformCookie)).andExpect(status().isForbidden());

        // Branch operasyon ayarları (BUSINESS_SETTINGS_MANAGE / BRANCH_MANAGE)
        mockMvc.perform(get("/api/staff/business").cookie(platformCookie)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/branches/{branchId}/business-hours", branchId).cookie(platformCookie))
                .andExpect(status().isForbidden());

        // İşletme-içi personel yönetimi (STAFF_MANAGE) - /api/platform-admin/**'ten farklı, bu business-scoped olan
        mockMvc.perform(get("/api/staff/staff-users").cookie(platformCookie)).andExpect(status().isForbidden());

        // Denetim kaydı (AUDIT_VIEW)
        mockMvc.perform(get("/api/staff/audit").cookie(platformCookie)).andExpect(status().isForbidden());
    }

    @Test
    void platformAdminCanStillManageItsOwnAccountAndUseThePlatformAdminPanel() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Scope Narrowing Own Account Business");
        MockCookie platformCookie = new MockCookie(
                StaffCookieSupport.COOKIE_NAME,
                StaffFixtures.bootstrapPlatformAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "scope-platform-self@example.com"));

        // Kendi hesabı: /me hâlâ çalışır (permission gerektirmez).
        mockMvc.perform(get("/api/staff/auth/me").cookie(platformCookie))
                .andExpect(status().isOk());

        // Platform yönetimi (/api/platform-admin/**) rol bazlı çalışmaya devam ediyor.
        mockMvc.perform(get("/api/platform-admin/businesses").cookie(platformCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/platform-admin/businesses/{businessId}/staff-users", businessId).cookie(platformCookie))
                .andExpect(status().isOk());
    }
}
