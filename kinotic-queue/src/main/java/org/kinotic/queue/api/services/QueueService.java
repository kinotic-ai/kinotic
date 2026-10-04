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
     * @return the name of the queue a worker group's rejected records, and records it was leased five times without
     * accepting, are appended to; created with the source queue's shard count when the first record arrives
     */
    static String deadLetterQueue(String queue, String groupName) {
        return queue + "." + groupName + ".dlq";
    }

    /**
     * Creates a queue unless a queue with the same name already exists.
     *
     * @param definition the name and shard count of the queue; at most 1024 shards
     * @return the stored definition, which keeps its original shard count when the queue already existed
     */
    Future<QueueDefinition> createQueueIfNotExist(QueueDefinition definition);

    /**
     * Deletes the queue, with its records and the positions of its consumers and worker groups, on every queue node.
     * Appends to the queue fail from then on, until a queue with the name is created again, which is a new, empty
     * queue. The deleted queue's subscriptions and workers end, reporting the deletion to their exception handlers, the
     * next time they reach the queue, and never read a queue created later. A queue node away during the deletion
     * deletes its copies when it returns. The queue's {@link #deadLetterQueue dead-letter queues} are queues of their
     * own and stay.
     *
     * @param name the queue name
     * @return completes once the deletion is recorded, also when no queue had the name; every queue node stops
     * serving the queue as it learns of the deletion and deletes its copies within a second of it
     */
    Future<Void> deleteQueue(String name);

    /**
     * Appends a record to the shard its key hashes to. Records appended through the same queue node to the same
     * shard, which records with the same key are, are written in the order their appends were called; an append the
     * queue retries after a lost reply or a change of the shard's owner is written once, as long as fewer than 64 MB
     * of other records were written to the shard after it. Completes once a majority of the shard's copies has the
     * record, and fails when the queue does not exist, the key and payload together exceed
     * {@code kinotic.maxEventPayloadSize} bytes, or no majority of copies is reachable. A failed append may still
     * have been written, so retrying it can deliver the record twice.
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
     * are delivered. One subscription per consumer may be open at a time. A consumer's committed position on a shard
     * expires once no subscription of the consumer was open or committed there for the queue's retention period
     * ({@code kinotic.queue.retentionPeriod}), and the consumer then starts there at {@code startPosition} again. A
     * handler that throws closes the subscription. Fails when the queue does not exist, or, starting at the latest
     * record, when no majority of a shard's copies answers with the consumer's committed position.
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
     * time, with no ordering between records. A record counts as done once a worker accepts it, or once it is in the
     * group's {@link #deadLetterQueue dead-letter queue} after a worker rejected it or it was leased five times without
     * being accepted, not counting leases a worker closed before handing to its handler. Delivery is at least once: a record is leased again when its lease expires, when its worker
     * releases it or closes, and when the shard's owner changes before the group's progress past it reached the
     * shard's other copies. The group's position on a shard expires once no worker of the group asked for records there
     * for the queue's retention period, and the group then starts there at its start position again. Fails when the queue does not exist, or when the queue and group names together are too
     * long to name the dead-letter queue.
     *
     * @param queue     the queue name
     * @param groupName identifies the group; same character rules as a queue name
     * @param options   where the group starts, how many records the worker holds and for how long
     * @return the worker, which delivers on the calling Vert.x context
     */
    Future<QueueWorker> work(String queue, String groupName, WorkerOptions options);

}
