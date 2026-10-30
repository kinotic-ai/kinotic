package org.kinotic.queue.internal.log;

/**
 * Where an entry sat in the batch it was appended in, read without the entry's key or payload.
 *
 * @param offset the entry's offset
 * @param slot   where the record sat in its batch; null for a marker or membership change
 * @param size   the entry's {@link ShardEntry#size() size}
 */
public record RecordSlot(long offset, BatchSlot slot, long size) {
}
