package org.kinotic.queue.internal.cluster;

import org.kinotic.queue.api.model.QueueDefinition;

/**
 * A queue as the cluster stores it.
 *
 * @param definition  the queue's name and shard count
 * @param incarnation tells the queue from earlier queues of the same name
 */
public record StoredQueue(QueueDefinition definition, String incarnation) {

    public String name() {
        return definition.name();
    }
}
