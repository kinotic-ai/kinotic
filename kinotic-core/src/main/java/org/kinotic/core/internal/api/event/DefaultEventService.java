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
import io.vertx.core.spi.cluster.RegistrationInfo;
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
import org.kinotic.core.api.event.EventService;
import org.kinotic.core.api.event.EventStreamService;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.internal.KinoticIgniteClusterManager;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Routes raw events to the {@link EventBusService} or the {@link EventStreamService} by their scheme, publishes
 * typed events to their topic address, and delivers confirmed events as an Ignite compute job to the nodes that
 * registered a consumer of their type, waiting for every one's answer and retrying against the registrations of
 * the moment until each has handled it.
 *
 * Created by navid on 12/19/19
 */
@Slf4j
@Component
public class DefaultEventService implements EventService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000L;
    // bounds a whole broadcast, so one node that has not answered within it fails the attempt for every node; a
    // caller is held for at most MAX_ATTEMPTS of these plus the retry delays between them. ConfirmedEventDispatchJob
    // waits on a node's consumers no longer than this, so a consumer that never completes does not hold an Ignite thread
    static final long NODE_TIMEOUT_MS = 5000L;

    private final EventBusService eventBusService;
    private final EventStreamService eventStreamService;
    private final JsonMapper jsonMapper;
    private final Ignite ignite;
    private final KinoticIgniteClusterManager clusterManager;
    private final Vertx vertx;
    private final LongCounter requestCounter;
    private final LongCounter resultCounter;
    private final LongHistogram durationHistogram;
    private final LongCounter retryCounter;

    public DefaultEventService(EventBusService eventBusService,
                               EventStreamService eventStreamService,
                               JsonMapper jsonMapper,
                               Ignite ignite,
                               KinoticIgniteClusterManager clusterManager,
                               Vertx vertx,
                               OpenTelemetry openTelemetry) {
        this.eventBusService = eventBusService;
        this.eventStreamService = eventStreamService;
        this.jsonMapper = jsonMapper;
        this.ignite = ignite;
        this.clusterManager = clusterManager;
        this.vertx = vertx;
        Meter meter = openTelemetry.getMeter("kinotic.events");
        requestCounter = meter.counterBuilder("event.confirmed.requests")
                              .setDescription("Events published with confirmation")
                              .setUnit("events")
                              .build();
        resultCounter = meter.counterBuilder("event.confirmed.results")
                             .setDescription("Confirmed event deliveries, by outcome")
                             .setUnit("results")
                             .build();
        durationHistogram = meter.histogramBuilder("event.confirmed.duration")
                                 .setDescription("Time until every consuming node handled a confirmed event")
                                 .setUnit("ms")
                                 .ofLongs()
                                 .build();
        retryCounter = meter.counterBuilder("event.confirmed.retries")
                            .setDescription("Retried confirmed event deliveries")
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
    public void publish(Object event) {
        Validate.notNull(event, "event must not be null");
        Metadata metadata = Metadata.create();
        metadata.put(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_TYPE_JSON);
        eventBusService.publish(Event.create(ConsumerMethodRegistry.topicOf(event.getClass().getName()),
                                             metadata,
                                             jsonMapper.writeValueAsBytes(event)));
    }

    @Override
    public Future<Void> publishConfirmed(Object event) {
        Validate.notNull(event, "event must not be null");
        String eventType = event.getClass().getName();
        Attributes attributes = Attributes.builder().put("event.type", eventType).build();
        requestCounter.add(1, attributes);
        long startedAt = System.currentTimeMillis();
        ConfirmedEventDispatchJob job = new ConfirmedEventDispatchJob(eventType, jsonMapper.writeValueAsBytes(event));
        Context context = vertx.getOrCreateContext();
        return attempt(job, eventType, context, 1)
                .onComplete(ar -> {
                    Attributes result = Attributes.builder()
                                                  .put("event.type", eventType)
                                                  .put("result", ar.succeeded() ? "success" : "failure")
                                                  .build();
                    durationHistogram.record(System.currentTimeMillis() - startedAt, result);
                    resultCounter.add(1, result);
                });
    }

    // The consuming nodes are read once per attempt, so an attempt is delivered to, and reported on, one set of
    // nodes; read again on the next attempt, so a node that left is not waited for and a node that registered a
    // consumer meanwhile receives the event
    private Future<Void> attempt(ConfirmedEventDispatchJob job, String eventType, Context context, int attempt) {
        return consumingNodes(eventType, context).compose(nodes -> {
            Future<Void> ret;
            if (nodes.isEmpty()) {
                log.debug("No node consumes {}, so no node handles it", eventType);
                ret = Future.succeededFuture();
            } else {
                ret = broadcast(ignite.cluster().forNodes(nodes), job, context).recover(error -> {
                    Future<Void> retried;
                    if (attempt < MAX_ATTEMPTS) {
                        log.warn("Delivery of {} to {} nodes failed on attempt {} of {}: {}", eventType, nodes.size(),
                                 attempt, MAX_ATTEMPTS, error.getMessage());
                        retryCounter.add(1, Attributes.builder().put("event.type", eventType).build());
                        retried = vertx.timer(RETRY_DELAY_MS).compose(id -> attempt(job, eventType, context, attempt + 1));
                    } else {
                        log.error("Delivery of {} was not acknowledged by every node after {} attempts", eventType, MAX_ATTEMPTS, error);
                        retried = Future.failedFuture(error);
                    }
                    return retried;
                });
            }
            return ret;
        });
    }

    // the nodes holding a registration of the type's topic address, which every node with a consumer of the type
    // registers, less any whose node already left. The registrations are read on an Ignite thread, so the outcome
    // is handed back to the caller's context
    private Future<Collection<ClusterNode>> consumingNodes(String eventType, Context context) {
        Promise<List<RegistrationInfo>> promise = Promise.promise();
        clusterManager.getClusterRegistrations(ConsumerMethodRegistry.topicOf(eventType).baseResource(), promise);
        return Future.fromCompletionStage(promise.future().toCompletionStage(), context).map(registrations -> {
            Set<UUID> nodeIds = registrations.stream()
                                             .map(registration -> UUID.fromString(registration.nodeId()))
                                             .collect(Collectors.toSet());
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
