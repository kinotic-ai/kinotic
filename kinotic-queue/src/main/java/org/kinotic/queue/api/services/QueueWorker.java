package org.kinotic.queue.api.services;

import io.vertx.core.Future;
import io.vertx.core.streams.ReadStream;
import org.kinotic.queue.api.model.WorkItem;

/**
 * One worker of a group sharing a queue's records: each record is leased to one worker of the group at a time. A
 * record the worker neither settles nor {@link #renew renews} within its lease, or still holds when it closes, is
 * leased to another worker, and a record leased five times without being accepted is dropped. Methods must be called on the Vert.x context the
 * worker delivers on.
 */
public interface QueueWorker extends ReadStream<WorkItem> {

    /**
     * Marks the item as done, so the group never receives it again.
     *
     * @return completes once a majority of the shard's copies has stored the group's progress past the item, or the
     * shard's owner has noted the item as done while an earlier record is still out; fails when the item's lease has
     * expired, since the item may then be leased to another worker
     */
    Future<Void> accept(WorkItem item);

    /**
     * Hands the item back, so the group receives it again right away.
     *
     * @return fails when the item's lease has expired
     */
    Future<Void> release(WorkItem item);

    /**
     * Marks the item as one no worker can process, so the group never receives it again.
     *
     * @return completes as {@link #accept} does; fails when the item's lease has expired
     */
    Future<Void> reject(WorkItem item);

    /**
     * Starts the item's lease over, so a worker still processing the item keeps it for another lease duration with the
     * same delivery count.
     *
     * @return fails when the item's lease has expired
     */
    Future<Void> renew(WorkItem item);

    /**
     * Stops delivery and hands back every item the worker holds.
     *
     * @return completes once the items are handed back
     */
    Future<Void> close();

}
