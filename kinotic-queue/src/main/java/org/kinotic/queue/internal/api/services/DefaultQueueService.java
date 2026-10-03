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
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.api.services.QueueSubscription;
import org.kinotic.queue.internal.cluster.QueueClusterClient;
import org.kinotic.queue.internal.cluster.QueueDefinitionRepository;
import org.kinotic.queue.internal.cluster.QueueNode;
import org.kinotic.queue.internal.log.QueueLog;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

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

    @Override
    public Future<QueueDefinition> createQueueIfNotExist(QueueDefinition definition) {
        return vertx.executeBlocking(() -> {
            QueueLog.requireValidName(definition.name());
            Validate.isTrue(definition.shardCount() > 0 && definition.shardCount() <= MAX_SHARD_COUNT,
                            "shardCount must be between 1 and %d", MAX_SHARD_COUNT);
            QueueDefinition stored = definitions.saveIfAbsent(definition);
            // Keeps a copy on disk, so the queue is known again after every queue node restarts
            queueNode.localLog(stored);
            return stored;
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
        return definition(queue).compose(definition -> client.append(definition, key, payload));
    }

    @Override
    public Future<QueueSubscription> subscribe(String queue, String consumerName, StartPosition startPosition) {
        if (!QueueLog.isValidName(consumerName) || startPosition == null) {
            return Future.failedFuture(new IllegalArgumentException("Invalid consumer name '" + consumerName + "' or missing startPosition"));
        }
        Context context = vertx.getOrCreateContext();
        return definition(queue).compose(definition -> DefaultQueueSubscription.open(context, client, definition, consumerName, startPosition));
    }

    private Future<QueueDefinition> definition(String queue) {
        Future<QueueDefinition> ret;
        QueueDefinition known = queue != null ? definitions.findKnown(queue) : null;
        if (known != null) {
            ret = Future.succeededFuture(known);
        } else {
            ret = vertx.executeBlocking(() -> {
                QueueLog.requireValidName(queue);
                QueueDefinition definition = definitions.find(queue);
                Validate.isTrue(definition != null, "No queue named %s", queue);
                return definition;
            }, false);
        }
        return ret;
    }
}
