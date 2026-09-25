/**
 * What a {@link WatchEvent} records happened to a watched record. Each value arrives with the write
 * that produces it.
 */
export enum WatchEventKind {
    /** A condition was set beside the authority's fields. */
    CONDITION_SET = 'CONDITION_SET',
    /** A condition was cleared. */
    CONDITION_CLEARED = 'CONDITION_CLEARED',
    /** The record's status changed: the node reported it, or the platform recorded it. */
    STATUS_CHANGED = 'STATUS_CHANGED',
    /** What the record should be changed. */
    DESIRED_UPDATED = 'DESIRED_UPDATED',
    /** What the record should be was written again as new, so its worker answers it again. */
    DESIRED_RENEWED = 'DESIRED_RENEWED',
    /** The record's authority reported what it is. */
    OBSERVED_REPORTED = 'OBSERVED_REPORTED',
    /** The record's deletion was asked for; its worker finalizes it. */
    DELETION_REQUESTED = 'DELETION_REQUESTED'
}
