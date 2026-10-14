package org.kinotic.queue.api.model;

import java.time.Duration;

/**
 * How a {@link org.kinotic.queue.api.services.QueueWorker} takes work from its group.
 *
 * @param startPosition where the group starts on shards it has never finished a record on
 * @param prefetch      how many records the worker aims to hold before it settles some, at least one; since it asks
 *                      every shard for records at once, it can hold up to one more per shard
 * @param leaseDuration how long the worker may hold a record, from when it is leased or last renewed, before it is
 *                      handed to another worker of the group
 */
public record WorkerOptions(StartPosition startPosition, int prefetch, Duration leaseDuration) {
}
