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
import org.kinotic.core.api.event.ClusterEvent;
import org.kinotic.core.api.event.ClusterEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * Broadcasts each event as an Ignite compute task to the nodes carrying the attributes it names and waits for every
 * one's answer, retrying against the cluster's current membership until each has handled it.
 *
 * Created By Nic Padilla on 2/12/25
 */
@Slf4j
@Component
public class DefaultClusterEventPublisher implements ClusterEventPublisher {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000L;
    // bounds a whole broadcast, so one node that has not answered within it fails the attempt for every node; the
    // handlers act in memory, so such a node is down, not slow. A caller is held for at most MAX_ATTEMPTS of these
    // plus the retry delays between them
    private static final long NODE_TIMEOUT_MS = 5000L;

    private final Ignite ignite;
    private final Vertx vertx;
    private final LongCounter requestCounter;
    private final LongCounter resultCounter;
    private final LongHistogram durationHistogram;
    private final LongCounter retryCounter;

    public DefaultClusterEventPublisher(Ignite ignite, Vertx vertx, OpenTelemetry openTelemetry) {
        this.ignite = ignite;
        this.vertx = vertx;
        Meter meter = openTelemetry.getMeter("kinotic.cluster.events");
        requestCounter = meter.counterBuilder("cluster.event.requests")
                              .setDescription("Events published to the cluster")
                              .setUnit("events")
                              .build();
        resultCounter = meter.counterBuilder("cluster.event.results")
                             .setDescription("Cluster event deliveries, by outcome")
                             .setUnit("results")
                             .build();
        durationHistogram = meter.histogramBuilder("cluster.event.duration")
                                 .setDescription("Time until every node handled a cluster event")
                                 .setUnit("ms")
                                 .ofLongs()
                                 .build();
        retryCounter = meter.counterBuilder("cluster.event.retries")
                            .setDescription("Retried cluster event deliveries")
                            .setUnit("retries")
                            .build();
    }

    @Override
    public Future<Void> publish(ClusterEvent event) {
        Validate.notNull(event, "event must not be null");
        Validate.notNull(event.nodeAttributeFilter(), "The event names no node attribute filter");
        String eventType = event.getClass().getSimpleName();
        Attributes attributes = Attributes.builder().put("event.type", eventType).build();
        requestCounter.add(1, attributes);
        long startedAt = System.currentTimeMillis();
        ClusterEventTask task = new ClusterEventTask(event);
        return attempt(task, event.nodeAttributeFilter(), eventType, 1)
                .onComplete(ar -> {
                    Attributes result = Attributes.builder()
                                                  .put("event.type", eventType)
                                                  .put("result", ar.succeeded() ? "success" : "failure")
                                                  .build();
                    durationHistogram.record(System.currentTimeMillis() - startedAt, result);
                    resultCounter.add(1, result);
                });
    }

    // The membership is read once per attempt, so an attempt is delivered to, and reported on, one set of nodes;
    // read again on the next attempt, so a node that left is not waited for and a node that joined meanwhile
    // receives the event
    private Future<Void> attempt(ClusterEventTask task, Set<String> nodeAttributes, String eventType, int attempt) {
        Collection<ClusterNode> nodes = nodesOf(nodeAttributes).nodes();
        Future<Void> ret;
        if (nodes.isEmpty()) {
            log.debug("No node of the cluster is selected by {}", eventType);
            ret = Future.succeededFuture();
        } else {
            ret = broadcast(ignite.cluster().forNodes(nodes), task).recover(error -> {
                Future<Void> retried;
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("Delivery of {} to {} nodes failed on attempt {} of {}: {}", eventType, nodes.size(),
                             attempt, MAX_ATTEMPTS, error.getMessage());
                    retryCounter.add(1, Attributes.builder().put("event.type", eventType).build());
                    retried = vertx.timer(RETRY_DELAY_MS).compose(id -> attempt(task, nodeAttributes, eventType, attempt + 1));
                } else {
                    log.error("Delivery of {} was not acknowledged by every node after {} attempts", eventType, MAX_ATTEMPTS, error);
                    retried = Future.failedFuture(error);
                }
                return retried;
            });
        }
        return ret;
    }

    // the nodes carrying every attribute named, whatever its value; the whole cluster when none is
    private ClusterGroup nodesOf(Set<String> nodeAttributes) {
        ClusterGroup group = ignite.cluster();
        for (String name : nodeAttributes) {
            group = group.forAttribute(name, null);
        }
        return group;
    }

    // the Ignite future completes on an Ignite thread, so the outcome is handed back to the caller's context
    private Future<Void> broadcast(ClusterGroup nodes, ClusterEventTask task) {
        Promise<Void> promise = Promise.promise();
        Context context = vertx.getOrCreateContext();
        ignite.compute(nodes)
              .withTimeout(NODE_TIMEOUT_MS)
              .broadcastAsync(task)
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
