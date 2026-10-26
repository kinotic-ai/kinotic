package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.internal.utils.EventUtil;
import org.kinotic.gateway.internal.endpoints.Services;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the streams a connection's client receives within what the client takes. It counts the bytes
 * written to the client that the socket has not sent yet. While that backlog is above a high water mark,
 * each stream whose producer declared itself suspendable is suspended as its next value arrives, and every
 * suspended stream is resumed once the backlog drains below a low water mark. A stream that cannot be
 * suspended is to be cancelled when one of its values arrives with more of its own values unsent than its cap.
 * Control events go to the address the client sent the request to.
 */
@Slf4j
@RequiredArgsConstructor
public class ReplyFlowControl {

    private static final long HIGH_WATER_MARK_BYTES = 1024 * 1024;
    private static final long LOW_WATER_MARK_BYTES = 256 * 1024;
    // above the largest frame the gateway relays, a 10 MB Loki tail frame, so one value never trips it alone
    private static final long STREAM_BACKLOG_CAP_BYTES = 32 * 1024 * 1024;
    // a producer that declared itself suspendable and still sends this long after a suspend is ignoring it
    private static final long SUSPEND_GRACE_MILLIS = 5_000;

    private final Services services;
    // every field is touched only on the connection's event loop, where its writes complete and its replies arrive
    private long unsentBytes;
    // by correlation id
    private final Map<String, SuspendedStream> suspendedStreams = new HashMap<>();
    // by correlation id, for each stream that cannot be suspended: its own bytes written and not yet sent
    private final Map<String, Long> unsentByUnsuspendableStream = new HashMap<>();

    /**
     * Counts a frame written to the client until the socket has sent it.
     * @param write the write, which completes once the socket has sent the frame or the connection closed
     * @param bytes the frame's size
     * @param correlationId the request the frame answers, or null for a frame that answers none
     */
    public void written(Future<Void> write, int bytes, String correlationId) {
        unsentBytes += bytes;
        if (correlationId != null) {
            unsentByUnsuspendableStream.computeIfPresent(correlationId, (_, unsent) -> unsent + bytes);
        }
        write.onComplete(_ -> {
            unsentBytes -= bytes;
            if (correlationId != null) {
                unsentByUnsuspendableStream.computeIfPresent(correlationId, (_, unsent) -> unsent - bytes);
            }
            if (unsentBytes < LOW_WATER_MARK_BYTES && !suspendedStreams.isEmpty()) {
                resumeAll();
            }
        });
    }

    /**
     * Decides what happens to a stream value about to be written to the client, suspending the stream while
     * the client is behind.
     * @param correlationId the request the stream answers
     * @param invocation the request's state, which records whether its stream may be suspended
     * @param value the stream value
     * @return false when the stream cannot be suspended and more of its values wait unsent than its cap, so the
     *         stream is to be cancelled instead of the value written
     */
    public boolean admit(String correlationId, IncomingInvocation invocation, Event<byte[]> value) {
        boolean ret = true;
        if (!invocation.isValueSeen()) {
            invocation.setValueSeen(true);
            invocation.setSuspendable(Boolean.parseBoolean(value.metadata().get(EventConstants.SUSPENDABLE_HEADER)));
        }
        if (unsentBytes >= HIGH_WATER_MARK_BYTES && invocation.isSuspendable()) {
            suspendOrCheck(correlationId, invocation);
        }
        if (!invocation.isSuspendable()) {
            long streamUnsent = unsentByUnsuspendableStream.computeIfAbsent(correlationId, _ -> 0L);
            if (unsentBytes >= HIGH_WATER_MARK_BYTES && !invocation.isUnsuspendableWarned()) {
                invocation.setUnsuspendableWarned(true);
                log.warn("Stream {} from {} cannot be suspended, and its client is {} bytes behind",
                         correlationId, invocation.getDestination().raw(), unsentBytes);
            }
            ret = streamUnsent < STREAM_BACKLOG_CAP_BYTES;
        }
        return ret;
    }

    /**
     * @return how many of a stream's bytes are written and not yet sent, counted for a stream that cannot be suspended
     */
    public long unsentBytes(String correlationId) {
        return unsentByUnsuspendableStream.getOrDefault(correlationId, 0L);
    }

    /**
     * Cancels a stream at the service producing it, and forgets it.
     */
    public void cancel(String correlationId, IncomingInvocation invocation) {
        forget(correlationId);
        sendControl(invocation.getDestination(), correlationId, EventConstants.CONTROL_VALUE_CANCEL);
    }

    /**
     * The request ended, so its stream needs no resume and its bytes no count.
     */
    public void forget(String correlationId) {
        suspendedStreams.remove(correlationId);
        unsentByUnsuspendableStream.remove(correlationId);
    }

    private void suspendOrCheck(String correlationId, IncomingInvocation invocation) {
        long now = System.currentTimeMillis();
        SuspendedStream suspended = suspendedStreams.get(correlationId);
        if (suspended == null) {
            suspendedStreams.put(correlationId, new SuspendedStream(invocation.getDestination(), now));
            sendControl(invocation.getDestination(), correlationId, EventConstants.CONTROL_VALUE_SUSPEND);
        } else if (now - suspended.suspendedAt() > SUSPEND_GRACE_MILLIS) {
            // values already in flight when the suspend was sent arrive well within the grace period
            log.warn("Stream {} from {} declared it can be suspended but kept sending {} ms after a suspend; "
                             + "it is treated as one that cannot be suspended",
                     correlationId, invocation.getDestination().raw(), now - suspended.suspendedAt());
            invocation.setSuspendable(false);
            // the warning above names the stream, so admit adds no second one for it
            invocation.setUnsuspendableWarned(true);
            suspendedStreams.remove(correlationId);
            // a producer whose late values were only delayed would otherwise stay suspended with nothing to resume it
            sendControl(invocation.getDestination(), correlationId, EventConstants.CONTROL_VALUE_RESUME);
        }
    }

    private void resumeAll() {
        suspendedStreams.forEach((correlationId, suspended) ->
                sendControl(suspended.destination(), correlationId, EventConstants.CONTROL_VALUE_RESUME));
        suspendedStreams.clear();
    }

    private void sendControl(CRI destination, String correlationId, String control) {
        services.eventBusService.publish(EventUtil.createStreamControlEvent(destination, correlationId, control));
    }

}
