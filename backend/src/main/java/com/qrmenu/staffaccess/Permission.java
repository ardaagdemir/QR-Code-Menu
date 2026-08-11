package com.qrmenu.staffaccess;

/**
 * Section 2: "kritik işlemler yalnızca rol adıyla değil, permission kontrolleriyle
 * korunur ... böylece rol isimlendirmesi değişse veya yeni roller eklense bile yetki
 * kontrolü noktaları etkilenmez." Every staff-facing endpoint checks one of these, never
 * a StaffRole directly - see StaffRole.permissions() for the role->permission mapping.
 */
public enum Permission {
    MENU_MANAGE,
    BRANCH_MANAGE,
    QR_MANAGE,
    STAFF_MANAGE,
    KITCHEN_DECIDE,
    REFUND_ISSUE,
    ORDER_HISTORY_VIEW,
    AUDIT_VIEW,
    ORDERING_TOGGLE,
    ORDER_COMPLETE,
    ORDER_VIEW,
    ORDER_ACCEPT,
    ORDER_REJECT,
    BUSINESS_SETTINGS_MANAGE,
    ANNOUNCEMENT_MANAGE,
    REPORT_VIEW,
    REPORT_CHAIN_VIEW
}
