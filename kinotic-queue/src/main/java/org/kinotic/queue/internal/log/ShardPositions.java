package org.kinotic.queue.internal.log;

import java.util.Map;

/**
 * The positions of the consumers and of the worker groups on one shard.
 *
 * @param consumers the position of each consumer, by consumer name
 * @param groups    the position of each worker group, its low watermark, by group name
 */
public record ShardPositions(Map<String, ConsumerPosition> consumers, Map<String, ConsumerPosition> groups) {

    /**
     * No positions at all.
     */
    public static final ShardPositions NONE = new ShardPositions(Map.of(), Map.of());
}
