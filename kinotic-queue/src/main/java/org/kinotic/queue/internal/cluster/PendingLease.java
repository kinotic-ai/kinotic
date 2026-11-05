package org.kinotic.queue.internal.cluster;

import io.vertx.core.Promise;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;

/**
 * A lease request a dispatcher holds until it has records to lease.
 *
 * @param timerId the timer that answers the request with no records once it was held long enough
 */
record PendingLease(String workerId, int max, long leaseMillis, Promise<LeaseResponse> promise, long timerId) {
}
