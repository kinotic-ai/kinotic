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
 * suspended is to be cancelled when one of its values arrives with the backlog over its cap.
 */
@Slf4j
@RequiredArgsConstructor
public class ReplyFlowControl {

    private static final long HIGH_WATER_MARK_BYTES = 1024 * 1024;
    private static final long LOW_WATER_MARK_BYTES = 256 * 1024;
    // above the largest frame the gateway relays, a 10 MB Loki tail frame, so one value never trips it alone
    private static final long BACKLOG_CAP_BYTES = 32 * 1024 * 1024;
    // a producer that declared itself suspendable and still sends this long after a suspend is ignoring it
    private static final long SUSPEND_GRACE_MILLIS = 5_000;

    private final Services services;
    // every field is touched only on the connection's event loop, where its writes complete and its replies arrive
    private long unsentBytes;
    // by correlation id
    private final Map<String, SuspendedStream> suspendedStreams = new HashMap<>();

    /**
     * Counts a frame written to the client until the socket has sent it.
     * @param write the write, which completes once the socket has sent the frame or the connection closed
     * @param bytes the frame's size
     */
    public void written(Future<Void> write, int bytes) {
        unsentBytes += bytes;
        write.onComplete(_ -> {
            unsentBytes -= bytes;
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
     * @return false when the stream cannot be suspended and the client is further behind than the cap, so the
     *         stream is to be cancelled instead of the value written
     */
    public boolean admit(String correlationId, IncomingInvocation invocation, Event<byte[]> value) {
        boolean ret = true;
        if (!invocation.isValueSeen()) {
            invocation.setValueSeen(true);
            invocation.setSuspendable(Boolean.parseBoolean(value.metadata().get(EventConstants.SUSPENDABLE_HEADER)));
        }
        if (unsentBytes >= HIGH_WATER_MARK_BYTES) {
            String originCri = value.metadata().get(EventConstants.ORIGIN_CRI_HEADER);
            if (invocation.isSuspendable() && originCri != null) {
                suspendOrCheck(correlationId, invocation, CRI.create(originCri));
            }
            if (!invocation.isSuspendable()) {
                if (!invocation.isUnsuspendableWarned()) {
                    invocation.setUnsuspendableWarned(true);
                    log.warn("Stream {} from {} cannot be suspended, and its client is {} bytes behind",
                             correlationId, originCri, unsentBytes);
                }
                ret = unsentBytes < BACKLOG_CAP_BYTES;
            }
        }
        return ret;
    }

    /**
     * @return how far behind the client is, in bytes written and not yet sent
     */
    public long unsentBytes() {
        return unsentBytes;
    }

    /**
     * The request ended, so its stream needs no resume.
     */
    public void forget(String correlationId) {
        suspendedStreams.remove(correlationId);
    }

    private void suspendOrCheck(String correlationId, IncomingInvocation invocation, CRI originCri) {
        long now = System.currentTimeMillis();
        SuspendedStream suspended = suspendedStreams.get(correlationId);
        if (suspended == null) {
            suspendedStreams.put(correlationId, new SuspendedStream(originCri, now));
            sendControl(originCri, correlationId, EventConstants.CONTROL_VALUE_SUSPEND);
        } else if (now - suspended.suspendedAt() > SUSPEND_GRACE_MILLIS) {
            // values already in flight when the suspend was sent arrive well within the grace period
            log.warn("Stream {} from {} declared it can be suspended but kept sending {} ms after a suspend; "
                             + "it is treated as one that cannot be suspended",
                     correlationId, originCri.raw(), now - suspended.suspendedAt());
            invocation.setSuspendable(false);
            // the warning above names the stream, so admit adds no second one for it
            invocation.setUnsuspendableWarned(true);
            suspendedStreams.remove(correlationId);
            // a producer whose late values were only delayed would otherwise stay suspended with nothing to resume it
            sendControl(originCri, correlationId, EventConstants.CONTROL_VALUE_RESUME);
        }
    }

    private void resumeAll() {
        suspendedStreams.forEach((correlationId, suspended) ->
                sendControl(suspended.originCri(), correlationId, EventConstants.CONTROL_VALUE_RESUME));
        suspendedStreams.clear();
    }

    private void sendControl(CRI originCri, String correlationId, String control) {
        services.eventBusService.publish(EventUtil.createStreamControlEvent(originCri, correlationId, control));
    }

}
