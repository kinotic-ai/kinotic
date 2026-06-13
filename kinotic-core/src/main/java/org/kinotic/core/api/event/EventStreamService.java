


package org.kinotic.core.api.event;

import io.vertx.core.Future;
import org.reactivestreams.Publisher;

/**
 * Provides functionality to send and receive events from the event streams.
 * Event streams are persistent events that are maintained throughout the cluster.
 *
 *
 * Created by navid on 10/22/19
 */
public interface EventStreamService {

    Future<Void> send(Event<byte[]> event);

    /**
     * Sends a stream of events to the underlying stream storage
     * The returned Future completes successfully if all the outbound records are delivered successfully.
     * The {@link Future} fails on the first send failure.
     * If publisher is a non-terminating publisher, records continue to be sent to the underlying stream storage unless a send fails or the returned Future is cancelled.
     * @param publisher to receive events from
     * @return the {@link Future} representing this request
     */
    Future<Void> sendStream(Publisher<Event<byte[]>> publisher);

    EventConsumer listen(CRI cri);
}
