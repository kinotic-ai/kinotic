package org.kinotic.core.internal.api.event;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.RpcRequestService;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.service.RequestLivenessWatcher;
import org.kinotic.core.api.utils.KinoticUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link RpcRequestService}: one reply consumer per node, with each pending request
 * keyed by its correlation id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRpcRequestService implements RpcRequestService {

    private final EventBusService eventBusService;
    private final RequestLivenessWatcher requestLivenessWatcher;
    // absent on a server that keeps no service directory
    private final ObjectProvider<ServiceDirectory> serviceDirectory;
    private final ConcurrentHashMap<String, Promise<Event<byte[]>>> pendingRequests = new ConcurrentHashMap<>();
    private final CRI replyCri = CRI.create(EventConstants.REPLY_DESTINATION_SCHEME,
                                            UUID.randomUUID().toString(),
                                            "org.kinotic.core.RpcRequestService");
    private EventConsumer replyConsumer;

    @PostConstruct
    void init() {
        replyConsumer = eventBusService.listen(replyCri);
        replyConsumer.handler(reply -> {
            String correlationId = reply.metadata().get(EventConstants.CORRELATION_ID_HEADER);
            Promise<Event<byte[]>> pending = correlationId != null ? pendingRequests.remove(correlationId) : null;
            if (correlationId != null) {
                requestLivenessWatcher.unwatch(correlationId);
            }
            if (pending == null) {
                // the request already failed, or the service replied more than once
                log.debug("Discarding reply with correlation id {}", correlationId);
            } else {
                pending.complete(reply);
            }
        });
        replyConsumer.completion()
                     .onFailure(throwable -> log.error("RpcRequestService reply consumer registration failed", throwable));
    }

    @PreDestroy
    void shutdown() {
        replyConsumer.unregister();
    }

    @Override
    public Future<Event<byte[]>> request(Event<byte[]> request) {
        String correlationId = UUID.randomUUID().toString();
        Promise<Event<byte[]>> ret = Promise.promise();
        pendingRequests.put(correlationId, ret);

        request.metadata().put(EventConstants.REPLY_TO_HEADER, replyCri.raw());
        request.metadata().put(EventConstants.CORRELATION_ID_HEADER, correlationId);

        eventBusService.sendWithAck(request)
                       .onComplete(ar -> {
                           if (ar.failed()) {
                               // only the party that removes the entry completes it: a reply can land before an
                               // acknowledgement times out
                               Promise<Event<byte[]>> unsent = pendingRequests.remove(correlationId);
                               if (unsent != null) {
                                   unsent.fail(KinoticUtil.mapSendFailure(ar.cause(), request.cri(), serviceDirectory.getIfAvailable()));
                               }
                           } else {
                               // computeIfPresent serializes with the reply handler's remove, so a reply that lands
                               // before the acknowledgement is processed leaves nothing watched
                               pendingRequests.computeIfPresent(correlationId, (_, pending) -> {
                                   requestLivenessWatcher.watch(correlationId, ar.result(), () -> {
                                       Promise<Event<byte[]>> lost = pendingRequests.remove(correlationId);
                                       if (lost != null) {
                                           lost.fail(new RpcServiceUnavailableException("The node serving " + request.cri().raw()
                                                                                                + " left before replying"));
                                       }
                                   });
                                   return pending;
                               });
                           }
                       });
        return ret.future();
    }
}
