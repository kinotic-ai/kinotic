package org.kinotic.auth.parsers;

/**
 * Thrown when a policy expression string cannot be parsed.
 */
public class PolicyParseException extends RuntimeException {

    public PolicyParseException(String message) {
        super(message);
    }

    public PolicyParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
