package org.kinotic.auth.spel;

/**
 * Thrown when SpEL policy evaluation cannot be attempted: no policy is registered for the action,
 * or the request payload cannot be parsed.
 */
public class SpelAuthorizationException extends RuntimeException {
    public SpelAuthorizationException(String message) {
        super(message);
    }

    public SpelAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
