

package org.kinotic.rpc.internal.api.service.rpc;

/**
 * {@link RpcRequest} is provided so that {@link RpcReturnValueHandler}'s will be able to provided a deferred request pattern.
 * This is useful for objects that expect that the actual work will not be done until some method is performed on the return value.
 * Such as subscribing to a {@link reactor.core.publisher.Mono} or {@link reactor.core.publisher.Flux}
 *
 *
 * Created by navid on 10/30/19
 */
public interface RpcRequest {

    /**
     * Sends an event that will trigger the invocation on the remote end
     */
    void send();

    /**
     * Sends a control event to the remote end to cancel a long running invocation
     */
    void cancelRequest();

}
