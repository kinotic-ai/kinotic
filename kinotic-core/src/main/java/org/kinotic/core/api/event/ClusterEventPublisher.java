package org.kinotic.core.api.event;

import io.vertx.core.Future;

/**
 * Delivers a {@link ClusterEvent} to the nodes of the servers it is for, this node among them when it is one, and
 * says when every one of them has handled it: the guaranteed counterpart of a fire-and-forget broadcast, for the
 * changes those nodes must act on, such as a cache to evict or a connection to end.
 */
public interface ClusterEventPublisher {

    /**
     * Hands {@code event} to the {@link ClusterEventHandler}s of its type on every node carrying the
     * {@link ClusterEvent#nodeAttributeFilter() attributes it names} and completes once each has handled it; a filter
     * no node matches is handled as nothing. A node that
     * fails or does not answer in time is retried against the cluster's current membership, so a node that left is
     * not waited for and one that joined meanwhile receives it; a node that already handled the event handles it
     * again on a retry, so the event's handlers must be idempotent. Fails once the retries are spent, with the last
     * node's failure as the cause.
     *
     * @param event the event
     * @return a future completed once every selected node has handled the event
     */
    Future<Void> publish(ClusterEvent event);

}
