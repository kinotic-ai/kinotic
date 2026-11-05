package org.kinotic.queue.internal.api.services;

/**
 * A record leased to a worker and not yet settled.
 *
 * @param deliveryCount the delivery the lease is for; a settlement of an earlier delivery of the same record does not
 *                      end it
 * @param timerId       the timer that lets the record go once its lease runs out
 */
record HeldLease(int deliveryCount, long timerId) {
}
