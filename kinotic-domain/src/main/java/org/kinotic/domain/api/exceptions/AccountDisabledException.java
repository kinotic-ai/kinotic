package org.kinotic.domain.api.exceptions;

/**
 * An invitation was accepted by an account that may not sign in to the invitation's organization: the
 * account is disabled, or its membership of the organization is. The invitation is not consumed.
 */
public class AccountDisabledException extends IllegalArgumentException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
