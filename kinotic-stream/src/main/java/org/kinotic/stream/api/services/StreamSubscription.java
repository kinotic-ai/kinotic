package org.kinotic.stream.api.services;

import io.vertx.core.Future;
import io.vertx.core.streams.ReadStream;
import org.kinotic.stream.api.model.StreamRecord;

/**
 * The records of one stream delivered to one consumer, in offset order within each shard.
 * Methods must be called on the Vert.x context the subscription delivers on.
 */
public interface StreamSubscription extends ReadStream<StreamRecord> {

    /**
     * Commits the consumer's position through {@code record} on the record's shard, so the consumer's next
     * subscription resumes after it. Committing a record at or before an already committed one has no effect.
     *
     * @param record a record this subscription delivered
     * @return completes once the position is stored
     */
    Future<Void> commit(StreamRecord record);

    /**
     * Stops delivery and calls the end handler. Records delivered but not committed are delivered again to
     * the consumer's next subscription.
     *
     * @return completes once the subscription has released its shards
     */
    Future<Void> close();

}
