package org.kinotic.auth.cedar;

/**
 * Thrown when the Cedar native library cannot be loaded or JNI methods cannot be accessed.
 */
public class CedarInitializationException extends RuntimeException {
    public CedarInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
