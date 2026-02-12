

package org.kinotic.rpc.api.event;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Provides functionality to send and receive events from the event streams.
 * Event streams are persistent events that are maintained throughout the cluster.
 *
 *
 * Created by navid on 10/22/19
 */
public interface EventStreamService {

    Mono<Void> send(Event<byte[]> event);

    /**
     * Sends a stream of events to the underlying stream storage
     * The returned Mono completes successfully if all the outbound records are delivered successfully.
     * The {@link Mono} terminates on the first send failure.
     * If publisher is a non-terminating {@link Flux}, records continue to be sent to the underlying stream storage unless a send fails or the returned Mono is cancelled.
     * @param publisher to receive events from
     * @return the {@link Mono} representing this request
     */
    Mono<Void> sendStream(Publisher<Event<byte[]>> publisher);

    Flux<Event<byte[]>> listen(CRI cri);
}

