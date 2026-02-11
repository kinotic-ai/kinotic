package org.kinotic.continuum.api.exceptions;

/**
 * Base exception class for all Continuum exceptions
 * Created by Navíd Mitchell 🤪 on 7/12/23.
 */
public class ContinuumException extends RuntimeException {
    public ContinuumException() {
    }

    public ContinuumException(String message) {
        super(message);
    }

    public ContinuumException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContinuumException(Throwable cause) {
        super(cause);
    }

    public ContinuumException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
