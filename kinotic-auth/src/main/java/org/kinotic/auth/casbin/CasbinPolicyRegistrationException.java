package org.kinotic.auth.casbin;

/**
 * Thrown when an ABAC expression cannot be parsed or compiled into a jCasbin policy.
 */
public class CasbinPolicyRegistrationException extends RuntimeException {
    public CasbinPolicyRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
