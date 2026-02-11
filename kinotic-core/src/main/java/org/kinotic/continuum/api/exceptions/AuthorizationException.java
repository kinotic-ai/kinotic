package org.kinotic.continuum.api.exceptions;

/**
 * Represents an error during authorization.
 * Created by Navíd Mitchell 🤪on 7/11/23.
 */
public class AuthorizationException extends ContinuumException{

    public AuthorizationException() {
    }

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthorizationException(Throwable cause) {
        super(cause);
    }

    public AuthorizationException(String message,
                                  Throwable cause,
                                  boolean enableSuppression,
                                  boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
