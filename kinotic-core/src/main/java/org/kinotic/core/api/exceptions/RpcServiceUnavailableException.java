package org.kinotic.core.api.exceptions;

/**
 * Thrown when the node serving an RPC request left the cluster while the request was in flight, when the
 * request's acknowledgement never arrived, or when the serving node shut down a stream it was producing.
 * The request may or may not have executed, which is what separates this from
 * {@link RpcMissingServiceException}, a request rejected at send that never ran.
 *
 * Created by Navíd Mitchell 🤪 on 9/9/26.
 */
public class RpcServiceUnavailableException extends KinoticException {

    public RpcServiceUnavailableException(String message) {
        super(message);
    }

    public RpcServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
