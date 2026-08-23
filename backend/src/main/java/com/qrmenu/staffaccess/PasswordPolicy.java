package com.qrmenu.staffaccess;

/** Single source of truth for the staff password length rule - referenced by every
 * password-bearing request DTO (create/change/reset) so the rule never drifts between them. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }
}
