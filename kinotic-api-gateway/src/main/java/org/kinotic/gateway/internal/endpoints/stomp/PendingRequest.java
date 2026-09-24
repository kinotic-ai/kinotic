package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.api.security.Participant;

/**
 * A request a client sent that has not ended: how to answer it, who sent it, and when it went out
 * and first heard back, in {@link System#nanoTime()} readings.
 */
@Getter
@RequiredArgsConstructor
class PendingRequest {

    private final Metadata replyMetadata;
    private final Participant caller;
    private final long sentAt;
    private Long firstReplyAt;

    /**
     * Notes a reply reaching the client; only the first one counts.
     */
    void replied(long now) {
        if (firstReplyAt == null) {
            firstReplyAt = now;
        }
    }

    /**
     * @return the time from the request to its first reply, or to now when none has arrived
     */
    long timeToFirstReply(long now) {
        return (firstReplyAt != null ? firstReplyAt : now) - sentAt;
    }
}
