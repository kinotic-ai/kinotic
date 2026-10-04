package org.kinotic.queue.internal.cluster;

/**
 * A record leased to one worker.
 *
 * @param workerId      the worker holding the record
 * @param deliveryCount how many times the record has been leased, including this lease
 * @param timerId       the timer that hands the record back to the group when the lease expires
 */
record Lease(String workerId, int deliveryCount, long timerId) {
}
