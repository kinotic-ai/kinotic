package org.kinotic.continuum.api.exceptions;

/**
 * This exception is thrown when an RPC request was made for a Service method that does not exist
 *
 * Created by Navíd Mitchell 🤪 on 5/12/22.
 */
public class RpcMissingMethodException extends ContinuumException{
    public RpcMissingMethodException() {
    }

    public RpcMissingMethodException(String message) {
        super(message);
    }

    public RpcMissingMethodException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcMissingMethodException(Throwable cause) {
        super(cause);
    }

    public RpcMissingMethodException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
