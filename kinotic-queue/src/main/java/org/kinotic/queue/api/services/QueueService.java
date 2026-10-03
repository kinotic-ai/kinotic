package org.kinotic.queue.api.services;

import io.vertx.core.Future;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.StartPosition;

/**
 * Appends records to durable, sharded, replicated queues and delivers them to named consumers, which resume
 * from the last position they committed. Every queue node in the cluster serves every queue.
 */
public interface QueueService {

    /**
     * Creates a queue unless a queue with the same name already exists.
     *
     * @param definition the name and shard count of the queue
     * @return the stored definition, which keeps its original shard count when the queue already existed
     */
    Future<QueueDefinition> createQueueIfNotExist(QueueDefinition definition);

    /**
     * Appends a record to the shard its key hashes to. Records appended with the same key are delivered in the
     * order they were appended. Completes once a majority of the shard's copies has the record, and fails when
     * the queue does not exist or no majority of copies is reachable. A failed append may still have been
     * written, so retrying it can deliver the record twice.
     *
     * @param queue   the queue name
     * @param key     decides the shard the record is written to
     * @param payload the record content
     * @return the position the record was written at
     */
    Future<QueuePosition> append(String queue, String key, byte[] payload);

    /**
     * Subscribes a named consumer to every shard of a queue. On each shard, delivery resumes after the last
     * record the consumer committed, or at {@code startPosition} when the consumer has never committed on that
     * shard. Delivery is at least once: every record after the last commit is delivered again to the next
     * subscription of the same consumer, on any queue node. Only records held by a majority of a shard's copies
     * are delivered. One subscription per consumer may be open at a time.
     * Fails when the queue does not exist.
     *
     * @param queue         the queue name
     * @param consumerName  identifies the consumer whose committed positions are resumed and updated; same
     *                      character rules as a queue name
     * @param startPosition where to start on shards this consumer has never committed on
     * @return the subscription, which delivers on the calling Vert.x context
     */
    Future<QueueSubscription> subscribe(String queue, String consumerName, StartPosition startPosition);

}
