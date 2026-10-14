package org.kinotic.queue.internal.cluster.message;

/**
 * One record of an {@link AppendRequest}.
 */
public record AppendRecord(String key, byte[] payload) {
}
