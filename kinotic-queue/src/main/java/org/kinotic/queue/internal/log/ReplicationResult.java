package org.kinotic.queue.internal.log;

/**
 * The outcome of {@link ShardLog#replicate}.
 *
 * @param status        how the shard handled the batch
 * @param nextOffset    when accepted, the offset after the batch; on a mismatch, the offset the owner resends from
 * @param conflictEpoch on a mismatch where the shard holds an entry of another epoch before the batch, that epoch, whose
 *                      first entry in the shard is {@code nextOffset}: an owner holding entries of it resends from its
 *                      last one, which the shard holds the same; otherwise -1
 */
public record ReplicationResult(ReplicationStatus status, long nextOffset, long conflictEpoch) {

    public ReplicationResult(ReplicationStatus status, long nextOffset) {
        this(status, nextOffset, -1);
    }
}
