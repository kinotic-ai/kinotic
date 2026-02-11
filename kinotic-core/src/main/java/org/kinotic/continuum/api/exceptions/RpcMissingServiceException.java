package org.kinotic.continuum.api.exceptions;

/**
 * This exception is thrown when an RPC request was made for a Service that does not exist
 *
 * Created by Navíd Mitchell 🤪 on 5/12/22.
 */
public class RpcMissingServiceException extends ContinuumException{
    public RpcMissingServiceException() {
    }

    public RpcMissingServiceException(String message) {
        super(message);
    }

    public RpcMissingServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcMissingServiceException(Throwable cause) {
        super(cause);
    }

    public RpcMissingServiceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
