package org.kinotic.queue.internal.cluster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The requests queue nodes send each other. Each kind is received on its own event bus address, and its sender waits
 * for the reply up to the kind's timeout.
 */
@RequiredArgsConstructor
enum QueueAction {
    // Longer than the owner waits for a majority, so the owner's answer arrives before the request gives up
    APPEND(QueueAction.MAJORITY_TIMEOUT_MS),
    // Well inside the owner's commit timeout, so a lost batch is resent before the appends waiting on it give up
    REPLICATE(ShardOwner.COMMIT_TIMEOUT_MS / 3),
    // An owner holds a fetch up to ShardOwner.FETCH_WAIT_MS before answering, so its reply takes longer
    FETCH(QueueAction.REQUEST_TIMEOUT_MS + ShardOwner.FETCH_WAIT_MS),
    COMMITTED_OFFSET(QueueAction.REQUEST_TIMEOUT_MS),
    READ(QueueAction.REQUEST_TIMEOUT_MS),
    PREPARE(QueueAction.REQUEST_TIMEOUT_MS),
    COMMIT_OFFSET(QueueAction.REQUEST_TIMEOUT_MS),
    FIND_OFFSET(QueueAction.REQUEST_TIMEOUT_MS),
    // A group's first lease on an owner waits for a majority to store the group's position before the owner holds it
    LEASE(QueueAction.MAJORITY_TIMEOUT_MS + ShardOwner.FETCH_WAIT_MS),
    SETTLE(QueueAction.MAJORITY_TIMEOUT_MS),
    STATUS(QueueAction.REQUEST_TIMEOUT_MS);

    private static final long REQUEST_TIMEOUT_MS = 5_000;
    private static final long MAJORITY_TIMEOUT_MS = 2 * ShardOwner.COMMIT_TIMEOUT_MS;

    /**
     * How long the sender waits for the reply, in milliseconds.
     */
    @Getter
    private final long timeoutMs;

    /**
     * @return the event bus address the queue node receives this kind of request on
     */
    String address(String nodeId) {
        return "kinotic.queue." + nodeId + "." + name();
    }
}
