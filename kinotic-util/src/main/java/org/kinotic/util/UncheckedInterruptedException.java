

package org.kinotic.util;

/**
 * Will be thrown as unchecked alternative to {@link InterruptedException}
 * Created by 🤓 on 5/13/21.
 */
public class UncheckedInterruptedException extends RuntimeException {

    public UncheckedInterruptedException() {
    }

    public UncheckedInterruptedException(String message) {
        super(message);
    }

    public UncheckedInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UncheckedInterruptedException(Throwable cause) {
        super(cause);
    }

    public UncheckedInterruptedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
