package com.qrmenu.common.web;

/**
 * Thrown when a request references an entity that does not exist, or that exists but
 * does not belong to the tenant/business scoping the request (tenant isolation check
 * failures deliberately surface as 404, not 403, to avoid confirming the id exists).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
