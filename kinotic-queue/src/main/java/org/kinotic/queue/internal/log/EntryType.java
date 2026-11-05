package org.kinotic.queue.internal.log;

/**
 * What a {@link ShardEntry} is.
 */
public enum EntryType {
    /**
     * A record appended to the queue.
     */
    RECORD,
    /**
     * The marker an owner writes first in its epoch.
     */
    MARKER,
    /**
     * A change of the copies whose majority counts toward a commit.
     */
    MEMBERSHIP;

    /**
     * @return the byte the type is stored and sent as
     */
    public byte code() {
        return (byte) ordinal();
    }

    /**
     * @return the type stored or sent as the byte
     */
    public static EntryType fromCode(byte code) {
        return values()[code];
    }
}
