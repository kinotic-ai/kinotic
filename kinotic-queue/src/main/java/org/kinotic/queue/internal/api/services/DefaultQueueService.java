package org.kinotic.queue.internal.api.services;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue.api.services.QueueSubscription;
import org.kinotic.queue.internal.cluster.QueueClusterClient;
import org.kinotic.queue.internal.cluster.QueueDefinitionRepository;
import org.kinotic.queue.internal.cluster.QueueNode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DefaultQueueService implements QueueService {

    // Queue and consumer names become directory and file names, so they can never contain a path separator or "..",
    // which also rules out path traversal
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final Vertx vertx;
    private final QueueDefinitionRepository definitions;
    private final QueueNode queueNode;
    private final QueueClusterClient client;

    @Override
    public Future<QueueDefinition> createQueueIfNotExist(QueueDefinition definition) {
        return vertx.executeBlocking(() -> {
            requireValidName(definition.name());
            Validate.isTrue(definition.shardCount() > 0, "shardCount must be at least one");
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
        return definition(queue).compose(definition -> client.append(definition, key, payload));
    }

    @Override
    public Future<QueueSubscription> subscribe(String queue, String consumerName, StartPosition startPosition) {
        if (consumerName == null || !NAME_PATTERN.matcher(consumerName).matches() || startPosition == null) {
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
                requireValidName(queue);
                QueueDefinition definition = definitions.find(queue);
                Validate.isTrue(definition != null, "No queue named %s", queue);
                return definition;
            }, false);
        }
        return ret;
    }

    private static void requireValidName(String name) {
        Validate.isTrue(name != null && NAME_PATTERN.matcher(name).matches(),
                        "Invalid name '%s': use up to 128 letters, digits, '.', '_' or '-', starting with a letter or digit",
                        name);
    }
}
