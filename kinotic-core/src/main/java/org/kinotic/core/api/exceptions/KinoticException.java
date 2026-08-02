package org.kinotic.core.api.exceptions;

/**
 * Base exception class for all Continuum exceptions
 * Created by Navíd Mitchell 🤪 on 7/12/23.
 */
public class KinoticException extends RuntimeException {
    public KinoticException() {
    }

    public KinoticException(String message) {
        super(message);
    }

    public KinoticException(String message, Throwable cause) {
        super(message, cause);
    }

    public KinoticException(Throwable cause) {
        super(cause);
    }

    public KinoticException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
