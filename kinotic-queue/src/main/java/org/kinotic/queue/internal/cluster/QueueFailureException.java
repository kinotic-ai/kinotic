package org.kinotic.queue.internal.cluster;

import lombok.Getter;

/**
 * A request a queue node refused, carrying the {@link QueueFailure} sent back to the requester.
 */
@Getter
public class QueueFailureException extends RuntimeException {

    private final QueueFailure failure;

    public QueueFailureException(QueueFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

}
