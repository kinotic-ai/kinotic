package org.kinotic.domain.api.model;

/**
 * What a {@link WatchEvent} records happened to a watched record. Each value arrives with the write
 * that produces it.
 */
public enum WatchEventKind {

    /**
     * A condition was set beside the authority's fields.
     */
    CONDITION_SET,

    /**
     * A condition was cleared.
     */
    CONDITION_CLEARED,

    /**
     * The record's status changed: the node reported it, or the platform recorded it.
     */
    STATUS_CHANGED,

    /**
     * What the record should be changed.
     */
    DESIRED_UPDATED,

    /**
     * The record's authority reported what it is.
     */
    OBSERVED_REPORTED
}
