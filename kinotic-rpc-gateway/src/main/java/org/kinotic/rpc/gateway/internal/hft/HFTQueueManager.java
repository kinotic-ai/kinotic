

package org.kinotic.rpc.gateway.internal.hft;

import org.kinotic.rpc.api.event.Event;
import reactor.core.publisher.Mono;

/**
 *
 * Created by Navid Mitchell on 11/4/20
 */
public interface HFTQueueManager {

    /**
     * Writes an {@link Event} to a HFT queue
     *
     * @param event to write to the queue
     *
     * @return a {@link Mono} that will succeed on successful writing and fail if there is an error
     */
    Mono<Void> write(Event<byte[]> event);

}
