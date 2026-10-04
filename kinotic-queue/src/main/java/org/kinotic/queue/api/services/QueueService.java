package org.kinotic.queue.api.services;

import io.vertx.core.Future;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.model.StartPosition;
import org.kinotic.queue.api.model.WorkerOptions;

/**
 * Appends records to durable, sharded, replicated queues and delivers them to named consumers, which resume
 * from the last position they committed. Every queue node in the cluster serves every queue.
 */
public interface QueueService {

    /**
     * Creates a queue unless a queue with the same name already exists.
     *
     * @param definition the name and shard count of the queue; at most 1024 shards
     * @return the stored definition, which keeps its original shard count when the queue already existed
     */
    Future<QueueDefinition> createQueueIfNotExist(QueueDefinition definition);

    /**
     * Appends a record to the shard its key hashes to. A record whose append completed before the append of another
     * record with the same key began is delivered before that record; appends that overlap may be written in either
     * order. Completes once a majority of the shard's copies has the record, and fails when
     * the queue does not exist, the key and payload together exceed {@code kinotic.maxEventPayloadSize} bytes, or
     * no majority of copies is reachable. A failed append may still have been written, so retrying it can deliver
     * the record twice; an append whose shard changes owner while it runs is retried internally and may be written
     * twice even when it succeeds.
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

    /**
     * Joins a group of workers that share a queue's records: each record is leased to one worker of the group at a
     * time, with no ordering between records. A record counts as done once a worker accepts or rejects it. Delivery
     * is at least once: a record is leased again when its lease expires, when its worker releases it or closes, and
     * when the shard's owner changes before the group's progress past it reached the shard's other copies.
     * Fails when the queue does not exist.
     *
     * @param queue     the queue name
     * @param groupName identifies the group; same character rules as a queue name
     * @param options   where the group starts, how many records the worker holds and for how long
     * @return the worker, which delivers on the calling Vert.x context
     */
    Future<QueueWorker> work(String queue, String groupName, WorkerOptions options);

}
