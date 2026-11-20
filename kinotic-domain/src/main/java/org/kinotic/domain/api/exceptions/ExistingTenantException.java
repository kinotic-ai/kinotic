package org.kinotic.domain.api.exceptions;

/**
 * A sign-up named a company the application already has a tenant for. Nothing is consumed; the person joins that
 * tenant by its administrator's invitation, or signs up under another name.
 */
public class ExistingTenantException extends IllegalArgumentException {

    public ExistingTenantException(String message) {
        super(message);
    }
}
