package org.kinotic.auth.casbin;

/**
 * Thrown when the jCasbin enforcer or its ABAC model cannot be initialized.
 */
public class CasbinInitializationException extends RuntimeException {
    public CasbinInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
