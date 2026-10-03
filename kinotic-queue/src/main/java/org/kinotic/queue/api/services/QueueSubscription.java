package org.kinotic.queue.api.services;

import io.vertx.core.Future;
import io.vertx.core.streams.ReadStream;
import org.kinotic.queue.api.model.QueueRecord;

/**
 * The records of one queue delivered to one consumer, in offset order within each shard.
 * Methods must be called on the Vert.x context the subscription delivers on.
 */
public interface QueueSubscription extends ReadStream<QueueRecord> {

    /**
     * Commits the consumer's position through {@code record} on the record's shard, so the consumer's next
     * subscription resumes after it. Committing a record at or before an already committed one has no effect.
     *
     * @param record a record this subscription delivered
     * @return completes once a majority of the shard's copies stores the position
     */
    Future<Void> commit(QueueRecord record);

    /**
     * Stops delivery and calls the end handler. Records delivered but not committed are delivered again to
     * the consumer's next subscription.
     *
     * @return completes once delivery has stopped
     */
    Future<Void> close();

}
