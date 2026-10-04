package org.kinotic.queue.internal.cluster;

import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;

/**
 * Why a queue node refused a request, sent as the failure code of the event bus reply.
 */
public enum QueueFailure {
    /**
     * The node does not own the shard, or is still recovering it; the sender looks up the owner again and retries.
     */
    NOT_OWNER,
    /**
     * Fewer copies of the shard are placed than a majority of the replication factor, so no write can complete.
     */
    NO_QUORUM,
    /**
     * A majority of the shard's copies did not confirm the record in time; it may still become committed later.
     */
    COMMIT_TIMEOUT,
    /**
     * The queue does not exist.
     */
    NO_QUEUE,
    /**
     * The record is not leased to the worker settling it, because its lease expired or its shard changed owner.
     */
    LEASE_EXPIRED;

    /**
     * @return the event bus failure code; codes start at one so they never collide with an unclassified failure
     */
    public int code() {
        return ordinal() + 1;
    }

    /**
     * @return the failure with the code, or null for a code no failure has
     */
    public static QueueFailure fromCode(int code) {
        QueueFailure[] failures = values();
        return code >= 1 && code <= failures.length ? failures[code - 1] : null;
    }

    public QueueFailureException exception(String message) {
        return new QueueFailureException(this, message);
    }

    /**
     * @return whether the failure of a request is this one, whether this node or the node the request went to refused it
     */
    public boolean matches(Throwable cause) {
        boolean ret = false;
        if (cause instanceof QueueFailureException failure) {
            ret = failure.getFailure() == this;
        } else if (cause instanceof ReplyException reply) {
            ret = reply.failureType() == ReplyFailure.RECIPIENT_FAILURE && fromCode(reply.failureCode()) == this;
        }
        return ret;
    }
}
