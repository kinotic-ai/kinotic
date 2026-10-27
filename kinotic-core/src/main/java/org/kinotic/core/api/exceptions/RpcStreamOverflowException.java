package org.kinotic.core.api.exceptions;

/**
 * Ends a stream whose client fell too far behind it when the stream's producer cannot be suspended. The
 * stream is cancelled at its producer; the client may request it again.
 */
public class RpcStreamOverflowException extends KinoticException {

    public RpcStreamOverflowException(String message) {
        super(message);
    }

}
