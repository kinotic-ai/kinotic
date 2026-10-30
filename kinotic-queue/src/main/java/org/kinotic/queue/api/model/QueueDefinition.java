package org.kinotic.queue.api.model;

/**
 * A named queue, the number of shards its records are spread across, and whether its writes are forced to disk.
 * The shard count and write syncing are fixed once the queue exists; the shard count decides which shard every key
 * belongs to.
 *
 * @param name       the queue name; letters, digits, {@code .}, {@code _} and {@code -}, starting with a letter or digit
 * @param shardCount the number of shards, at least one
 * @param syncWrites whether every write is forced to disk before it counts toward a majority of the shard's copies.
 *                   With it, an acknowledged record survives every copy losing power at once; without it, a record
 *                   survives as long as a majority of its copies keeps running or shuts down cleanly, and writes are
 *                   faster.
 */
public record QueueDefinition(String name, int shardCount, boolean syncWrites) {

    /**
     * A queue whose writes are forced to disk.
     */
    public QueueDefinition(String name, int shardCount) {
        this(name, shardCount, true);
    }
}
