package org.kinotic.core.api.exceptions;

/**
 * Thrown when creating an entity whose identity already exists. Lets callers surface a
 * friendly uniqueness message (e.g. "that name is taken") instead of silently overwriting
 * the existing entity.
 */
public class AlreadyExistsException extends KinoticException {

    public AlreadyExistsException(String message) {
        super(message);
    }

    public AlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns true when {@code throwable} is, or is caused by, an
     * {@link AlreadyExistsException} — future composition often wraps the original in a
     * {@link java.util.concurrent.CompletionException}.
     */
    public static boolean isCause(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof AlreadyExistsException) {
                return true;
            }
        }
        return false;
    }
}
