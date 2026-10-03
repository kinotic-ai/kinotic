package org.kinotic.queue.internal.log;

/**
 * The outcome of {@link ShardLog#replicate}.
 *
 * @param status     how the shard handled the batch
 * @param nextOffset the offset after the shard's last entry once the batch was handled
 */
public record ReplicationResult(ReplicationStatus status, long nextOffset) {
}
