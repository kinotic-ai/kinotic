package org.kinotic.stream.api.model;

/**
 * A named stream and the number of shards its records are spread across.
 * The shard count is fixed once the stream exists, since it decides which shard every key belongs to.
 *
 * @param name       the stream name; letters, digits, {@code .}, {@code _} and {@code -}, starting with a letter or digit
 * @param shardCount the number of shards, at least one
 */
public record StreamDefinition(String name, int shardCount) {
}
