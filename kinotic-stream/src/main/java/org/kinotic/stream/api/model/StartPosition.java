package org.kinotic.stream.api.model;

/**
 * Where a consumer starts reading a shard it has never committed a position on.
 */
public enum StartPosition {
    /**
     * Start at the first record in the shard.
     */
    EARLIEST,
    /**
     * Start at the next record appended to the shard after the subscription opens.
     */
    LATEST
}
