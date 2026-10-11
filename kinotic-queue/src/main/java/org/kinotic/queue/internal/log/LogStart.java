package org.kinotic.queue.internal.log;

/**
 * Where a shard's entries begin once older ones were deleted.
 *
 * @param offset      the offset of the shard's first entry, or of its next entry when it holds none
 * @param epochBefore the epoch of the deleted entry at {@code offset - 1}, which a batch continuing from there is checked
 *                    against; -1 when {@code offset} is zero
 */
public record LogStart(long offset, long epochBefore) {
}
