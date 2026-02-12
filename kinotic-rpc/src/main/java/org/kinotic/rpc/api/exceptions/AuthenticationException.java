package org.kinotic.rpc.api.exceptions;

import org.kinotic.rpc.api.security.SecurityService;

/**
 * Represents an error during authentication.
 * Should typically be used when implementing the {@link SecurityService}
 * Created by Navíd Mitchell 🤪on 7/11/23.
 */
public class AuthenticationException extends ContinuumException{
    public AuthenticationException() {
    }

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthenticationException(Throwable cause) {
        super(cause);
    }

    public AuthenticationException(String message,
                                   Throwable cause,
                                   boolean enableSuppression,
                                   boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
