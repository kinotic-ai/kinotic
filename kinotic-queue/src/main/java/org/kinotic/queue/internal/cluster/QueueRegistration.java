package org.kinotic.queue.internal.cluster;

import lombok.Value;
import org.kinotic.queue.api.model.QueueDefinition;

/**
 * A queue as the cluster's store of queue definitions holds it, under the queue's name.
 */
// A class rather than a record, since Ignite's binary marshaller cannot build records
@Value
public class QueueRegistration {

    int shardCount;
    boolean syncWrites;
    /**
     * Tells the queue from earlier queues of the same name.
     */
    String incarnation;

    /**
     * @return the queue registered under the name
     */
    StoredQueue toStoredQueue(String name) {
        return new StoredQueue(new QueueDefinition(name, shardCount, syncWrites), incarnation);
    }
}
