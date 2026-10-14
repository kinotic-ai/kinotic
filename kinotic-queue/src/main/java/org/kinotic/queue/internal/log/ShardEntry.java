package org.kinotic.queue.internal.log;

/**
 * An entry as a shard stores it: a record; the marker a new owner writes when it takes the shard; or a change of the
 * copies whose majority counts toward a commit. Consumers see only records.
 *
 * @param offset     the entry's position in its shard
 * @param epoch      the epoch of the owner that first wrote the entry
 * @param key        the key the record was appended with; null for a marker or membership change
 * @param payload    the record content; empty for a marker or membership change
 * @param slot       where the record sat in the batch it was appended in; null for a marker or membership change
 * @param membership the copies counting toward a commit from this entry on; null for a record or marker
 */
public record ShardEntry(long offset, long epoch, String key, byte[] payload, BatchSlot slot, Membership membership) {

    private static final byte[] NO_PAYLOAD = new byte[0];

    public static ShardEntry record(long offset, long epoch, String key, byte[] payload, BatchSlot slot) {
        return new ShardEntry(offset, epoch, key, payload, slot, null);
    }

    /**
     * The entry an owner writes first in its epoch. A record of an earlier epoch counts as committed only once a
     * majority holds this marker after it.
     */
    public static ShardEntry marker(long offset, long epoch) {
        return new ShardEntry(offset, epoch, null, NO_PAYLOAD, null, null);
    }

    /**
     * The entry that changes the copies counting toward a commit. Every copy counts by the last one it holds, committed
     * or not.
     */
    public static ShardEntry membership(long offset, long epoch, Membership membership) {
        return new ShardEntry(offset, epoch, null, NO_PAYLOAD, null, membership);
    }

    /**
     * @return whether the entry is a record appended to the queue, rather than an entry the shard's owners write
     */
    public boolean isRecord() {
        return key != null;
    }

    public boolean isMembership() {
        return membership != null;
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
