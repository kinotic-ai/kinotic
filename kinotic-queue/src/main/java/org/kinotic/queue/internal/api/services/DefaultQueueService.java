package org.kinotic.queue.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.queue.api.config.KinoticQueueProperties;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.model.WorkerOptions;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.api.services.QueueSubscription;
import org.kinotic.queue.api.services.QueueWorker;
import org.kinotic.queue.internal.cluster.QueueClusterClient;
import org.kinotic.queue.internal.cluster.QueueDefinitionRepository;
import org.kinotic.queue.internal.cluster.QueueNode;
import org.kinotic.queue.internal.cluster.ShardStateRepository;
import org.kinotic.queue.internal.cluster.StoredQueue;
import org.kinotic.queue.internal.log.ConsumerOffsetRepository;
import org.kinotic.queue.internal.log.QueueLog;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class DefaultQueueService implements QueueService {

    // Every shard is an open Chronicle Queue on each node holding it, and every node checks every shard's placement
    private static final int MAX_SHARD_COUNT = 1_024;

    private final Vertx vertx;
    private final KinoticQueueProperties properties;
    private final QueueDefinitionRepository definitions;
    private final QueueNode queueNode;
    private final QueueClusterClient client;
    private final ShardStateRepository shardStates;

    @Override
    public Future<QueueDefinition> createQueueIfNotExist(QueueDefinition definition) {
        return vertx.executeBlocking(() -> {
            QueueLog.requireValidName(definition.name());
            Validate.isTrue(definition.shardCount() > 0 && definition.shardCount() <= MAX_SHARD_COUNT,
                            "shardCount must be between 1 and %d", MAX_SHARD_COUNT);
            StoredQueue stored = definitions.saveIfAbsent(definition);
            // Keeps a copy on disk, so the queue is known again after every queue node restarts
            queueNode.localLog(stored);
            return stored.definition();
        }, false);
    }

    @Override
    public Future<Void> deleteQueue(String name) {
        return vertx.executeBlocking(() -> {
            QueueLog.requireValidName(name);
            StoredQueue deleted = definitions.delete(name);
            if (deleted != null) {
                shardStates.deleteAll(deleted.incarnation());
            }
            return null;
        }, false);
    }

    @Override
    public Future<QueuePosition> append(String queue, String key, byte[] payload) {
        if (key == null || payload == null) {
            return Future.failedFuture(new IllegalArgumentException("key and payload must not be null"));
        }
        long size = (long) key.getBytes(StandardCharsets.UTF_8).length + payload.length;
        if (size > properties.getMaxEventPayloadSize()) {
            return Future.failedFuture(new IllegalArgumentException("The record is " + size + " bytes, larger than kinotic.maxEventPayloadSize ("
                                                                            + properties.getMaxEventPayloadSize() + ")"));
        }
        // Handed to the client before the lookup completes, so appends keep the order they were called in
        return client.append(queue, definition(queue), key, payload);
    }

    @Override
    public Future<QueueSubscription> subscribe(String queue, String consumerName, StartPosition startPosition) {
        if (!QueueLog.isValidName(consumerName) || startPosition == null) {
            return Future.failedFuture(new IllegalArgumentException("Invalid consumer name '" + consumerName + "' or missing startPosition"));
        }
        Context context = vertx.getOrCreateContext();
        Duration refreshInterval = ConsumerOffsetRepository.refreshInterval(properties.getQueue().getRetentionPeriod());
        return definition(queue).compose(stored -> DefaultQueueSubscription.open(context, client, stored, consumerName, startPosition,
                                                                                 refreshInterval));
    }

    @Override
    public Future<QueueWorker> work(String queue, String groupName, WorkerOptions options) {
        if (!QueueLog.isValidName(groupName)
                || !QueueLog.isValidName(QueueService.deadLetterQueue(queue, groupName))
                || options == null
                || options.startPosition() == null
                || options.prefetch() < 1
                || options.leaseDuration() == null
                || options.leaseDuration().toMillis() < 1) {
            return Future.failedFuture(new IllegalArgumentException("Invalid group name '" + groupName + "', which with the queue name must also name"
                                                                            + " a valid dead-letter queue, or invalid options " + options));
        }
        Context context = vertx.getOrCreateContext();
        return definition(queue).compose(stored -> DefaultQueueWorker.open(context, client, stored, groupName, options));
    }

    private Future<StoredQueue> definition(String queue) {
        Future<StoredQueue> ret;
        StoredQueue known = queue != null ? definitions.findKnown(queue) : null;
        if (known != null) {
            ret = Future.succeededFuture(known);
        } else {
            ret = vertx.executeBlocking(() -> {
                QueueLog.requireValidName(queue);
                StoredQueue stored = definitions.findStored(queue);
                Validate.isTrue(stored != null, "No queue named %s", queue);
                return stored;
            }, false);
        }
        return ret;
    }
}
