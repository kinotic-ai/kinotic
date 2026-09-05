package org.kinotic.auth.cedar;

/**
 * Thrown when Cedar policy evaluation fails at runtime.
 */
public class CedarAuthorizationException extends RuntimeException {
    public CedarAuthorizationException(String message) {
        super(message);
    }

    public CedarAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
