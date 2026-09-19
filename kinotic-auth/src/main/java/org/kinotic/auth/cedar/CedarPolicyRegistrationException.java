package org.kinotic.auth.cedar;

/**
 * Thrown when an ABAC expression cannot be parsed or compiled into a Cedar policy.
 */
public class CedarPolicyRegistrationException extends RuntimeException {
    public CedarPolicyRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
