package org.kinotic.stream.api.services;

import io.vertx.core.Future;
import org.kinotic.stream.api.model.StartPosition;
import org.kinotic.stream.api.model.StreamDefinition;
import org.kinotic.stream.api.model.StreamPosition;

/**
 * Appends records to durable, sharded streams and delivers them to named consumers, which resume from
 * the last position they committed.
 */
public interface StreamService {

    /**
     * Creates a stream unless a stream with the same name already exists.
     *
     * @param definition the name and shard count of the stream
     * @return the stored definition, which keeps its original shard count when the stream already existed
     */
    Future<StreamDefinition> createStreamIfNotExist(StreamDefinition definition);

    /**
     * Appends a record to the shard its key hashes to. Records appended with the same key are delivered in
     * the order they were appended. Fails when the stream does not exist.
     *
     * @param stream  the stream name
     * @param key     decides the shard the record is written to
     * @param payload the record content
     * @return the position the record was written at
     */
    Future<StreamPosition> append(String stream, String key, byte[] payload);

    /**
     * Subscribes a named consumer to every shard of a stream. On each shard, delivery resumes after the last
     * record the consumer committed, or at {@code startPosition} when the consumer has never committed on that
     * shard. Delivery is at least once: every record after the last commit is delivered again to the next
     * subscription of the same consumer. One subscription per consumer may be open at a time.
     * Fails when the stream does not exist.
     *
     * @param stream        the stream name
     * @param consumerName  identifies the consumer whose committed positions are resumed and updated; same
     *                      character rules as a stream name
     * @param startPosition where to start on shards this consumer has never committed on
     * @return the subscription, which delivers on the calling Vert.x context
     */
    Future<StreamSubscription> subscribe(String stream, String consumerName, StartPosition startPosition);

}
