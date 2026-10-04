package org.kinotic.queue.internal.cluster;

import io.vertx.core.Promise;
import org.kinotic.queue.internal.cluster.message.LeaseResponse;

/**
 * A lease request a dispatcher holds until it has records to lease.
 */
record PendingLease(String workerId, int max, long leaseMillis, Promise<LeaseResponse> promise) {
}
