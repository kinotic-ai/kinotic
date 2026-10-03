package org.kinotic.queue.internal.log;

/**
 * How a follower's shard handled a batch of entries from the shard's owner.
 */
public enum ReplicationStatus {
    /**
     * The shard holds every entry the owner sent and agrees with the owner up to them.
     */
    ACCEPTED,
    /**
     * The batch did not continue the shard; the owner resends from the offset in the result, which is zero when the
     * shard held entries the owner does not have and was emptied.
     */
    MISMATCH,
    /**
     * The shard has accepted entries from a newer owner, so the sender no longer owns it.
     */
    STALE_EPOCH
}
