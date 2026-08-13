package com.qrmenu.staffaccess;

import java.util.EnumSet;
import java.util.Set;

/**
 * Section 5/12 (CONFIRMED): the three roles. permissions() is the one place the
 * role->permission mapping lives (Role -> Set&lt;Permission&gt;) - PLATFORM_ADMIN is
 * business_id-less (Section 5, "tek istisna") and today only used for the /internal/**
 * bootstrap API (shared admin token, not StaffUser login), so it isn't wired into any
 * staff-web-facing permission check yet; its permission set is still defined here for
 * data-model completeness and to not special-case it out of the enum's contract.
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
            case PLATFORM_ADMIN -> EnumSet.allOf(Permission.class);
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
                    Permission.ANNOUNCEMENT_MANAGE,
                    Permission.REPORT_VIEW,
                    Permission.REPORT_CHAIN_VIEW,
                    Permission.REPORT_FINANCIAL_SUMMARY_VIEW,
                    Permission.EXPENSE_VIEW,
                    Permission.EXPENSE_MANAGE,
                    Permission.EXPENSE_APPROVE);
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
