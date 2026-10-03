package org.kinotic.queue.internal.log;

/**
 * The outcome of {@link ShardLog#replicate}.
 *
 * @param status     how the shard handled the batch
 * @param nextOffset when accepted, the offset after the batch; on a mismatch, the offset the owner resends from
 */
public record ReplicationResult(ReplicationStatus status, long nextOffset) {
}
