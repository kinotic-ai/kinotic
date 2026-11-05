package org.kinotic.queue.internal.log;

import java.util.List;

/**
 * Entries a shard's owner sends to a copy of the shard, with what the copy needs to check that they continue its own
 * entries.
 *
 * @param epoch            the owner's epoch
 * @param prevOffset       the offset before the batch's first entry, or -1 when the batch starts the shard
 * @param prevEpoch        the epoch of the entry at {@code prevOffset} on the owner
 * @param ownerStartOffset the offset of the owner's first entry; the owner has deleted the entries before it
 * @param ownerNextOffset  the offset after the owner's last entry when the batch was read
 * @param entries          consecutive entries starting at {@code prevOffset + 1}
 */
public record ReplicationBatch(long epoch,
                               long prevOffset,
                               long prevEpoch,
                               long ownerStartOffset,
                               long ownerNextOffset,
                               List<ShardEntry> entries) {
}
