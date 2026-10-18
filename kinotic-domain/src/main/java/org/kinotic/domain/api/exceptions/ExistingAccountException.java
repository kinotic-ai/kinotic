package org.kinotic.domain.api.exceptions;

/**
 * A sign-up or an invitation was made for an identity or email that already has an account, by a method
 * that does not prove the account is the person's: a sign-up, which founds one account at most; a password
 * other than the account's; or a provider identity the account is not bound to. Nothing is consumed; the
 * person signs in as the account instead.
 */
public class ExistingAccountException extends IllegalArgumentException {

    public ExistingAccountException(String message) {
        super(message);
    }
}
