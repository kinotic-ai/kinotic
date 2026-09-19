package org.kinotic.auth.casbin;

/**
 * Thrown when jCasbin policy evaluation fails at runtime.
 */
public class CasbinAuthorizationException extends RuntimeException {
    public CasbinAuthorizationException(String message) {
        super(message);
    }

    public CasbinAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
