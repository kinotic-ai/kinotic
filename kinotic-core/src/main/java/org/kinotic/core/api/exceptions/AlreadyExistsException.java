package org.kinotic.core.api.exceptions;

/**
 * Thrown when creating an entity whose identity already exists. Lets callers surface a
 * friendly uniqueness message (e.g. "that name is taken") instead of silently overwriting
 * the existing entity.
 */
public class AlreadyExistsException extends ContinuumException {

    public AlreadyExistsException(String message) {
        super(message);
    }

    public AlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
