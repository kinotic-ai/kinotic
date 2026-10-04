package org.kinotic.queue.internal.log;

/**
 * An entry as a shard stores it: a record, or the marker a new owner writes when it takes the shard.
 *
 * @param offset  the entry's position in its shard
 * @param epoch   the epoch of the owner that first wrote the entry
 * @param key     the key the record was appended with; null for a marker
 * @param payload the record content; empty for a marker
 * @param slot    where the record sat in the batch it was appended in; null for a marker
 */
public record ShardEntry(long offset, long epoch, String key, byte[] payload, BatchSlot slot) {

    private static final byte[] NO_PAYLOAD = new byte[0];

    /**
     * The entry an owner writes first in its epoch. Consumers never see it; a record of an earlier epoch counts as
     * committed only once a majority holds this marker after it.
     */
    public static ShardEntry marker(long offset, long epoch) {
        return new ShardEntry(offset, epoch, null, NO_PAYLOAD, null);
    }

    public boolean isMarker() {
        return key == null;
    }

    /**
     * @return roughly the bytes the entry adds to a batch
     */
    public int size() {
        return size(key, payload);
    }

    /**
     * @return roughly the bytes a record with this key and payload adds to a batch
     */
    public static int size(String key, byte[] payload) {
        return payload.length + (key == null ? 0 : key.length());
    }
}
