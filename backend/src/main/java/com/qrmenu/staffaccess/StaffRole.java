package com.qrmenu.staffaccess;

import java.util.EnumSet;
import java.util.Set;

/**
 * Section 5/12 (CONFIRMED): the four roles. permissions() is the one place the
 * role->permission mapping lives (Role -> Set&lt;Permission&gt;) - PLATFORM_ADMIN is
 * business_id-less (Section 5, "tek istisna") and today only used for the /internal/**
 * bootstrap API (shared admin token, not StaffUser login), so it isn't wired into any
 * staff-web-facing permission check yet; its permission set is still defined here for
 * data-model completeness and to not special-case it out of the enum's contract.
 */
public enum StaffRole {
    PLATFORM_ADMIN,
    BUSINESS_ADMIN,
    BRANCH_MANAGER,
    KITCHEN_STAFF;

    public Set<Permission> permissions() {
        return switch (this) {
            case PLATFORM_ADMIN -> EnumSet.allOf(Permission.class);
            case BUSINESS_ADMIN -> EnumSet.of(
                    Permission.MENU_MANAGE,
                    Permission.BRANCH_MANAGE,
                    Permission.QR_MANAGE,
                    Permission.STAFF_MANAGE,
                    Permission.KITCHEN_DECIDE,
                    Permission.REFUND_ISSUE,
                    Permission.ORDER_HISTORY_VIEW,
                    Permission.AUDIT_VIEW,
                    Permission.ORDERING_TOGGLE,
                    Permission.ORDER_COMPLETE);
            case BRANCH_MANAGER -> EnumSet.of(
                    Permission.REFUND_ISSUE,
                    Permission.ORDER_HISTORY_VIEW,
                    Permission.ORDERING_TOGGLE,
                    Permission.KITCHEN_DECIDE,
                    Permission.ORDER_COMPLETE);
            case KITCHEN_STAFF -> EnumSet.of(Permission.KITCHEN_DECIDE);
        };
    }

    public boolean hasPermission(Permission permission) {
        return permissions().contains(permission);
    }
}
