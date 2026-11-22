package org.kinotic.core.api.event;

import io.vertx.core.Future;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;

/**
 * Sends an RPC request to one consumer of its destination and completes with the single reply that answers it,
 * for an entry point that turns one inbound request into one service invocation and one response.
 */
public interface RpcRequestService {

    /**
     * Sends the request and completes with the reply to it. A reply carrying {@link EventConstants#ERROR_HEADER},
     * which is how a service reports that the invocation failed, completes the future like any other reply.
     *
     * @param request the request, addressed to the service function's {@link CRI} and naming its sender; its
     *                {@link EventConstants#REPLY_TO_HEADER} and {@link EventConstants#CORRELATION_ID_HEADER} are
     *                set by this service
     * @return a future completing with the reply, or failing with {@link RpcMissingServiceException} when nothing
     *         serves the destination and with {@link RpcServiceUnavailableException} when the request was not
     *         acknowledged or the node that took it left the cluster before replying
     */
    Future<Event<byte[]>> request(Event<byte[]> request);

}
