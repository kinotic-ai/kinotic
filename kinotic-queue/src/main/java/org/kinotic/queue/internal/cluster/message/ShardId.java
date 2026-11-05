package org.kinotic.queue.internal.cluster.message;

/**
 * Names one shard of one queue. Every request about a shard carries it, so a request about a deleted queue is never
 * applied to a queue created later with the same name.
 *
 * @param queue       the queue's name
 * @param incarnation tells the queue from earlier queues of the same name
 * @param shard       the shard's index within the queue
 */
public record ShardId(String queue, String incarnation, int shard) {

    /**
     * @return the shard and its queue, as log and failure messages name them
     */
    @Override
    public String toString() {
        return "shard " + shard + " of queue " + queue;
    }
}
