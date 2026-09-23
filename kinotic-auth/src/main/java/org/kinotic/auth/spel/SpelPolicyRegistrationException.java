package org.kinotic.auth.spel;

/**
 * Thrown when an ABAC expression cannot be parsed or compiled into a SpEL expression.
 */
public class SpelPolicyRegistrationException extends RuntimeException {
    public SpelPolicyRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
