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
    REJECT,
    /**
     * The worker still processes the record, so its lease starts over.
     */
    RENEW,
    /**
     * The worker closed before handing the record to its handler, so the record goes back to the group without the
     * lease counting toward its deliveries, and the worker's waiting lease requests end with no records.
     */
    RETURN
}
