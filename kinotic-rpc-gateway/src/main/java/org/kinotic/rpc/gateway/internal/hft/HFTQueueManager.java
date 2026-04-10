


package org.kinotic.rpc.gateway.internal.hft;

import io.vertx.core.Future;
import org.kinotic.core.api.event.Event;

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
     * @return a {@link Future} that will succeed on successful writing and fail if there is an error
     */
    Future<Void> write(Event<byte[]> event);

}
