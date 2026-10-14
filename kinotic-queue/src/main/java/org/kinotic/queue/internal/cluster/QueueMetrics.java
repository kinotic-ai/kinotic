package org.kinotic.queue.internal.cluster;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This node's queue meters, registered with Micrometer's global registry, which the platform exports. Counters count
 * what this node did as the owner of shards. Gauges are refreshed by the queue node and sum, for each queue, over the
 * shards this node owns or holds a copy of, so summing a gauge across nodes gives the queue's total.
 */
@Component
public class QueueMetrics {

    private final MeterRegistry registry = Metrics.globalRegistry;
    private final Set<Meter> eventMeters = ConcurrentHashMap.newKeySet();
    private final MultiGauge consumerLag = MultiGauge.builder("kinotic.queue.consumer.lag")
                                                     .description("Committed records the consumer has not committed, over the shards this node owns")
                                                     .register(registry);
    private final MultiGauge groupLag = MultiGauge.builder("kinotic.queue.group.lag")
                                                  .description("Committed records the worker group has not finished, over the shards this node owns")
                                                  .register(registry);
    private final MultiGauge replicaLag = MultiGauge.builder("kinotic.queue.replica.lag")
                                                    .description("Records the copies on a node lack, over the shards this node owns")
                                                    .register(registry);
    private final MultiGauge size = MultiGauge.builder("kinotic.queue.size")
                                              .description("About how much space the copies of the queue's shards on this node take")
                                              .baseUnit("bytes")
                                              .register(registry);
    private final MultiGauge shardsOwned = MultiGauge.builder("kinotic.queue.shards.owned")
                                                     .description("The shards of the queue this node owns")
                                                     .register(registry);

    /**
     * Counts records whose append this node acknowledged as their shard's owner, and how long the batch took.
     */
    public void recordAppend(String queue, int records, Duration duration) {
        Counter appends = Counter.builder("kinotic.queue.appends")
                                 .description("Records whose append this node acknowledged as their shard's owner")
                                 .tag("queue", queue)
                                 .register(registry);
        Timer batches = Timer.builder("kinotic.queue.append.duration")
                             .description("How long this node took to commit a batch of appends as its shard's owner")
                             .tag("queue", queue)
                             .register(registry);
        eventMeters.add(appends);
        eventMeters.add(batches);
        appends.increment(records);
        batches.record(duration);
    }

    public void recordDeadLetter(String queue, String groupName) {
        count("kinotic.queue.dead.letters", "Records appended to a worker group's dead-letter queue", Tags.of("queue", queue, "group", groupName), 1);
    }

    /**
     * Counts records leased again after an earlier lease of them ended without the record being accepted.
     */
    public void recordRedeliveries(String queue, String groupName, int records) {
        count("kinotic.queue.redeliveries", "Records leased again to a worker group", Tags.of("queue", queue, "group", groupName), records);
    }

    public void recordTakeover(String queue) {
        count("kinotic.queue.takeovers", "Times this node took ownership of a shard", Tags.of("queue", queue), 1);
    }

    public void recordStepDown(String queue) {
        count("kinotic.queue.step.downs", "Times this node stopped owning a shard it still had placed on it", Tags.of("queue", queue), 1);
    }

    /**
     * Replaces the gauges' values.
     *
     * @param consumerLags by queue, then consumer
     * @param groupLags    by queue, then group
     * @param replicaLags  by queue, then node id of the copies
     * @param sizes        bytes by queue
     * @param owned        shards owned by queue
     */
    public void refresh(Map<String, Map<String, Long>> consumerLags,
                        Map<String, Map<String, Long>> groupLags,
                        Map<String, Map<String, Long>> replicaLags,
                        Map<String, Long> sizes,
                        Map<String, Long> owned) {
        consumerLag.register(rows(consumerLags, "consumer"), true);
        groupLag.register(rows(groupLags, "group"), true);
        replicaLag.register(rows(replicaLags, "replica"), true);
        size.register(rows(sizes), true);
        shardsOwned.register(rows(owned), true);
    }

    /**
     * Removes the counters of a deleted queue; its gauges go at the next {@link #refresh}.
     */
    public void removeQueue(String queue) {
        eventMeters.removeIf(meter -> {
            boolean ret = queue.equals(meter.getId().getTag("queue"));
            if (ret) {
                registry.remove(meter);
            }
            return ret;
        });
    }

    @PreDestroy
    public void close() {
        refresh(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        eventMeters.forEach(registry::remove);
        eventMeters.clear();
    }

    private void count(String name, String description, Tags tags, int amount) {
        Counter counter = Counter.builder(name).description(description).tags(tags).register(registry);
        eventMeters.add(counter);
        counter.increment(amount);
    }

    private static List<MultiGauge.Row<?>> rows(Map<String, Map<String, Long>> values, String tag) {
        List<MultiGauge.Row<?>> ret = new ArrayList<>();
        values.forEach((queue, byName) -> byName.forEach((name, value) -> ret.add(MultiGauge.Row.of(Tags.of("queue", queue, tag, name), value))));
        return ret;
    }

    private static List<MultiGauge.Row<?>> rows(Map<String, Long> byQueue) {
        List<MultiGauge.Row<?>> ret = new ArrayList<>();
        byQueue.forEach((queue, value) -> ret.add(MultiGauge.Row.of(Tags.of("queue", queue), value)));
        return ret;
    }
}
