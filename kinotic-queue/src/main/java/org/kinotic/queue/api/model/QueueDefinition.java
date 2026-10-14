package org.kinotic.queue.api.model;

/**
 * A named queue and the number of shards its records are spread across.
 * The shard count is fixed once the queue exists, since it decides which shard every key belongs to.
 *
 * @param name       the queue name; letters, digits, {@code .}, {@code _} and {@code -}, starting with a letter or digit
 * @param shardCount the number of shards, at least one
 */
public record QueueDefinition(String name, int shardCount) {
}
