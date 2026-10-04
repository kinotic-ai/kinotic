package org.kinotic.queue.internal.cluster.message;

/**
 * What a worker does with a leased record.
 */
public enum Settlement {
    /**
     * The record is done.
     */
    ACCEPT,
    /**
     * The record goes back to the group right away.
     */
    RELEASE,
    /**
     * The record can never be processed, so it is done.
     */
    REJECT
}
