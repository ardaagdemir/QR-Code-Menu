package com.qrmenu.common.web;

/**
 * Thrown by TenantService.assertBusinessAndBranchActive (and the authoritative
 * pre-payment gate it's folded into) when a business or one of its branches has been
 * deactivated by a PLATFORM_ADMIN - distinct from OrderingNotAllowedException (branch is
 * active but temporarily closed/outside hours): this is "the business/branch itself is
 * not currently in service," and maps to its own HTTP status (503) so customer-web can
 * show a dedicated message instead of the generic ordering-not-allowed one.
 */
public class BusinessUnavailableException extends RuntimeException {

    public BusinessUnavailableException(String message) {
        super(message);
    }
}
