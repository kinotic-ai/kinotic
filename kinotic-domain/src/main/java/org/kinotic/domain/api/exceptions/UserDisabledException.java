package org.kinotic.domain.api.exceptions;

/**
 * A disabled member of an organization tried to sign in to it. An invitation accepted this way is not consumed.
 */
public class UserDisabledException extends IllegalArgumentException {

    public UserDisabledException(String message) {
        super(message);
    }
}
