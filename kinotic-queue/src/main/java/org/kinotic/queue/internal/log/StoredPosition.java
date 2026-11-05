package org.kinotic.queue.internal.log;

/**
 * A position as a {@link ConsumerOffsetRepository} holds it in memory.
 *
 * @param nextOffset     the next offset to deliver; zero when none is stored
 * @param lastUsedMillis when the position was last used, in epoch milliseconds
 * @param version        the repository's version of the shard when the position was last written; zero when it was
 *                       read from disk and not written since
 */
record StoredPosition(long nextOffset, long lastUsedMillis, long version) {

    /**
     * No position stored.
     */
    static final StoredPosition NONE = new StoredPosition(0, 0, 0);

    /**
     * @return whether a position is stored and was used at or after {@code expiredBefore}, in epoch milliseconds
     */
    boolean isLive(long expiredBefore) {
        return nextOffset > 0 && lastUsedMillis >= expiredBefore;
    }

    ConsumerPosition toPosition() {
        return new ConsumerPosition(nextOffset, lastUsedMillis);
    }
}
