package com.qrmenu.staffaccess;

import java.util.EnumSet;
import java.util.Set;

/**
 * Section 5/12 (CONFIRMED): the three roles. permissions() is the one place the
 * role->permission mapping lives (Role -> Set&lt;Permission&gt;) - PLATFORM_ADMIN is
 * business_id-less (Section 5, "tek istisna").
 *
 * <p>PLATFORM_ADMIN scope narrowing: PLATFORM_ADMIN holds no regular-staff Permission at
 * all (deliberately EnumSet.noneOf, not EnumSet.allOf as before) - it manages
 * businesses/branches/staff-users exclusively through /api/platform-admin/**, which never
 * checks a Permission in the first place (PlatformAdminBusinessController/
 * PlatformAdminStaffController role-check PLATFORM_ADMIN directly). Every normal
 * business-operation endpoint (Kasa/orders, menu, tables, expenses, reports, refunds,
 * branch/business settings, business-scoped staff management) is gated behind a
 * Permission via StaffAuthService.requirePermission, so an empty permission set is what
 * actually keeps PLATFORM_ADMIN out of them - previously EnumSet.allOf(Permission.class)
 * let it through every one of those gates as a side effect, which is exactly the indirect
 * access this narrowing removes. A PLATFORM_ADMIN that needs to operate a business uses a
 * separate BUSINESS_ADMIN/BRANCH_MANAGER account instead.
 *
 * <p>Product decision (KDS simplification): there is no separate KITCHEN_STAFF role
 * anymore - sipariş operasyonunun tamamı (kabul/red + PREPARING/READY/COMPLETED akışı)
 * Kasa ekranından, BUSINESS_ADMIN/BRANCH_MANAGER/CASHIER tarafından yürütülür.
 */
public enum StaffRole {
    PLATFORM_ADMIN,
    BUSINESS_ADMIN,
    BRANCH_MANAGER,
    CASHIER;

    public Set<Permission> permissions() {
        return switch (this) {
            case PLATFORM_ADMIN -> EnumSet.noneOf(Permission.class);
            case BUSINESS_ADMIN -> EnumSet.of(
                    Permission.MENU_MANAGE,
                    Permission.BRANCH_MANAGE,
                    Permission.QR_MANAGE,
                    Permission.STAFF_MANAGE,
                    Permission.ORDER_PREPARE,
                    Permission.REFUND_ISSUE,
                    Permission.ORDER_HISTORY_VIEW,
                    Permission.AUDIT_VIEW,
                    Permission.ORDERING_TOGGLE,
                    Permission.ORDER_COMPLETE,
                    Permission.ORDER_VIEW,
                    Permission.ORDER_ACCEPT,
                    Permission.ORDER_REJECT,
                    Permission.BUSINESS_SETTINGS_MANAGE,
                    Permission.REPORT_VIEW,
                    Permission.REPORT_FINANCIAL_SUMMARY_VIEW,
                    Permission.EXPENSE_VIEW,
                    Permission.EXPENSE_MANAGE);
            case BRANCH_MANAGER -> EnumSet.of(
                    Permission.REFUND_ISSUE,
                    Permission.ORDER_HISTORY_VIEW,
                    Permission.ORDERING_TOGGLE,
                    Permission.ORDER_PREPARE,
                    Permission.ORDER_COMPLETE,
                    Permission.ORDER_VIEW,
                    Permission.ORDER_ACCEPT,
                    Permission.ORDER_REJECT,
                    Permission.REPORT_VIEW,
                    Permission.REPORT_FINANCIAL_SUMMARY_VIEW,
                    Permission.EXPENSE_VIEW,
                    Permission.EXPENSE_MANAGE);
            // Section 6/11: kasa - siparişi görür, kabul/red eder (tam refund'u tetikler) ve
            // kabul edilen siparişi PREPARING -> READY -> COMPLETED akışında ilerletir;
            // menü/personel/gider yönetimine dokunmaz.
            case CASHIER -> EnumSet.of(
                    Permission.ORDER_VIEW,
                    Permission.ORDER_ACCEPT,
                    Permission.ORDER_REJECT,
                    Permission.ORDER_PREPARE,
                    Permission.ORDER_COMPLETE,
                    Permission.ORDER_HISTORY_VIEW,
                    Permission.REPORT_VIEW);
        };
    }

    public boolean hasPermission(Permission permission) {
        return permissions().contains(permission);
    }
}
