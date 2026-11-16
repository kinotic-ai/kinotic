package org.kinotic.common.api.oidc;

/**
 * Thrown when an id token fails verification: its issuer, audience or nonce is not the one expected, it has expired,
 * or the provider's userinfo endpoint answers for another subject.
 */
public class InvalidIdTokenException extends RuntimeException {

    public InvalidIdTokenException(String message) {
        super(message);
    }

}
