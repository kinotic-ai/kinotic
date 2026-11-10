package org.kinotic.core.internal.api.event;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.cluster.ClusterNode;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.EventDelivery;
import org.kinotic.core.api.event.EventService;
import org.kinotic.core.api.event.EventStreamService;
import org.kinotic.core.api.event.Metadata;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Routes raw events to the {@link EventBusService} or the {@link EventStreamService} by their scheme. Publishes a
 * typed event to the best-effort address of its type, and delivers it as an Ignite compute job to the nodes that
 * registered a confirmed consumer of its type, waiting for every one's answer and retrying against the
 * registrations of the moment until each has handled it.
 *
 * Created by navid on 12/19/19
 * Created By Nic Padilla on 2/12/25
 */
@Slf4j
@Component
public class DefaultEventService implements EventService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000L;
    // bounds a whole broadcast, so one node that has not answered within it fails the attempt for every node; a
    // caller is held for at most MAX_ATTEMPTS of these plus the retry delays between them. It exceeds the time a node
    // waits on its consumers, so a node whose consumers are slow answers with its own failure before the broadcast
    // times out, and its job has ended before the retry reaches it
    private static final long NODE_TIMEOUT_MS = ConsumerMethodRegistry.CONFIRMED_TIMEOUT_MS + 1000L;

    private final EventBusService eventBusService;
    private final EventStreamService eventStreamService;
    private final JsonMapper jsonMapper;
    private final Ignite ignite;
    private final Vertx vertx;
    private final LongCounter requestCounter;
    private final LongCounter resultCounter;
    private final LongHistogram durationHistogram;
    private final LongCounter retryCounter;

    public DefaultEventService(EventBusService eventBusService,
                               EventStreamService eventStreamService,
                               JsonMapper jsonMapper,
                               Ignite ignite,
                               Vertx vertx,
                               OpenTelemetry openTelemetry) {
        this.eventBusService = eventBusService;
        this.eventStreamService = eventStreamService;
        this.jsonMapper = jsonMapper;
        this.ignite = ignite;
        this.vertx = vertx;
        Meter meter = openTelemetry.getMeter("kinotic.events");
        requestCounter = meter.counterBuilder("event.confirmed.requests")
                              .setDescription("Published events that had confirmed consumers")
                              .setUnit("events")
                              .build();
        resultCounter = meter.counterBuilder("event.confirmed.results")
                             .setDescription("Deliveries to confirmed consumers, by outcome")
                             .setUnit("results")
                             .build();
        durationHistogram = meter.histogramBuilder("event.confirmed.duration")
                                 .setDescription("Time until every node with a confirmed consumer handled an event")
                                 .setUnit("ms")
                                 .ofLongs()
                                 .build();
        retryCounter = meter.counterBuilder("event.confirmed.retries")
                            .setDescription("Retried deliveries to confirmed consumers")
                            .setUnit("retries")
                            .build();
    }

    @Override
    public Future<Void> send(Event<byte[]> event) {
        Future<Void> ret;
        if(event.cri().scheme().equals(EventConstants.SERVICE_DESTINATION_SCHEME)){
            ret = eventBusService.sendWithAck(event).mapEmpty();
        }else if(event.cri().scheme().equals(EventConstants.STREAM_DESTINATION_SCHEME)){
            ret = eventStreamService.send(event);
        }else{
            throw new IllegalArgumentException("Event cri must begin with "
                                                       + EventConstants.SERVICE_DESTINATION_SCHEME
                                                       + " or "
                                                       + EventConstants.STREAM_DESTINATION_SCHEME);
        }
        return ret;
    }

    @Override
    public EventConsumer listen(String cri) {
        EventConsumer ret;
        if(cri.startsWith(EventConstants.SERVICE_DESTINATION_SCHEME)){
            ret = eventBusService.listen(CRI.create(cri));
        }else if(cri.startsWith(EventConstants.STREAM_DESTINATION_SCHEME)){
            ret = eventStreamService.listen(CRI.create(cri));
        }else{
            throw new IllegalArgumentException("Event cri must begin with "
                                                       + EventConstants.SERVICE_DESTINATION_SCHEME
                                                       + " or "
                                                       + EventConstants.STREAM_DESTINATION_SCHEME);
        }
        return ret;
    }

    @Override
    public Future<Void> publish(Object event) {
        Future<Void> ret;
        try {
            ret = publishSerialized(Validate.notNull(event, "event must not be null").getClass().getName(),
                                    jsonMapper.writeValueAsBytes(event));
        } catch (RuntimeException e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

    private Future<Void> publishSerialized(String eventType, byte[] payload) {
        Metadata metadata = Metadata.create();
        metadata.put(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_TYPE_JSON);
        eventBusService.publish(Event.create(ConsumerMethodRegistry.addressOf(eventType, EventDelivery.BEST_EFFORT),
                                             metadata,
                                             payload));
        Context context = vertx.getOrCreateContext();
        return consumingNodes(eventType, context).compose(nodes -> {
            Future<Void> ret;
            if (nodes.isEmpty()) {
                ret = Future.succeededFuture();
            } else {
                ret = deliverConfirmed(eventType, payload, nodes, context);
            }
            return ret;
        });
    }

    private Future<Void> deliverConfirmed(String eventType,
                                          byte[] payload,
                                          Collection<ClusterNode> nodes,
                                          Context context) {
        requestCounter.add(1, Attributes.builder().put("event.type", eventType).build());
        long startedAt = System.currentTimeMillis();
        return attempt(eventType, payload, nodes, context, 1)
                .onComplete(ar -> {
                    Attributes result = Attributes.builder()
                                                  .put("event.type", eventType)
                                                  .put("result", ar.succeeded() ? "success" : "failure")
                                                  .build();
                    durationHistogram.record(System.currentTimeMillis() - startedAt, result);
                    resultCounter.add(1, result);
                });
    }

    // An attempt is delivered to, and reported on, one set of nodes. A retry reads the consuming nodes again, so a
    // node that left is not waited for and a node that registered a confirmed consumer meanwhile receives the event
    private Future<Void> attempt(String eventType,
                                 byte[] payload,
                                 Collection<ClusterNode> nodes,
                                 Context context,
                                 int attempt) {
        return broadcast(ignite.cluster().forNodes(nodes), new ConfirmedEventDispatchJob(eventType, payload), context).recover(error -> {
            Future<Void> retried;
            if (attempt < MAX_ATTEMPTS) {
                log.warn("Delivery of {} to {} nodes failed on attempt {} of {}: {}", eventType, nodes.size(),
                         attempt, MAX_ATTEMPTS, error.getMessage());
                retryCounter.add(1, Attributes.builder().put("event.type", eventType).build());
                retried = vertx.timer(RETRY_DELAY_MS)
                               .compose(id -> consumingNodes(eventType, context))
                               .compose(current -> current.isEmpty()
                                       ? Future.<Void>succeededFuture()
                                       : attempt(eventType, payload, current, context, attempt + 1));
            } else {
                log.error("Delivery of {} was not acknowledged by every node after {} attempts", eventType, MAX_ATTEMPTS, error);
                retried = Future.failedFuture(error);
            }
            return retried;
        });
    }

    // the nodes holding a registration of the type's confirmed address, which every node with a confirmed consumer
    // of the type registers, less any whose node already left. The registrations are read on an Ignite thread, so the
    // outcome is handed back to the caller's context
    private Future<Collection<ClusterNode>> consumingNodes(String eventType, Context context) {
        CRI confirmed = ConsumerMethodRegistry.addressOf(eventType, EventDelivery.CONFIRMED);
        return Future.fromCompletionStage(eventBusService.findListeningNodes(confirmed).toCompletionStage(), context).map(listening -> {
            Set<UUID> nodeIds = listening.stream().map(UUID::fromString).collect(Collectors.toSet());
            return nodeIds.isEmpty() ? List.<ClusterNode>of() : ignite.cluster().forNodeIds(nodeIds).nodes();
        });
    }

    // the Ignite future completes on an Ignite thread, so the outcome is handed back to the caller's context
    private Future<Void> broadcast(ClusterGroup nodes, ConfirmedEventDispatchJob job, Context context) {
        Promise<Void> promise = Promise.promise();
        ignite.compute(nodes)
              .withTimeout(NODE_TIMEOUT_MS)
              .broadcastAsync(job)
              .listen(done -> context.runOnContext(v -> {
                  try {
                      done.get();
                      promise.complete();
                  } catch (RuntimeException e) {
                      promise.fail(e);
                  }
              }));
        return promise.future();
    }

}
