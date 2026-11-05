package org.kinotic.queue.internal.log;

/**
 * How a follower's shard handled a batch of entries from the shard's owner.
 */
public enum ReplicationStatus {
    /**
     * The shard now holds the owner's entries up to the end of the batch.
     */
    ACCEPTED,
    /**
     * The entry before the batch differs from the owner's, or the shard does not reach it; the owner resends from the
     * offset in the result. The shard is left unchanged.
     */
    MISMATCH,
    /**
     * The shard has promised a newer owner, so the sender no longer owns it.
     */
    STALE_EPOCH
}
