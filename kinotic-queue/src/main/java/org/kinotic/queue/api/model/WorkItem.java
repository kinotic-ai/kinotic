package org.kinotic.queue.api.model;

/**
 * A record leased to one worker of a group.
 *
 * @param record        the record
 * @param deliveryCount how many times the record has been leased to the group's workers, including this time
 */
public record WorkItem(QueueRecord record, int deliveryCount) {
}
