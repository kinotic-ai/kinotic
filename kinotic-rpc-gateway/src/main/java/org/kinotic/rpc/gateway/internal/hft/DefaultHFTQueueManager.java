


package org.kinotic.rpc.gateway.internal.hft;

import io.vertx.core.Future;
import org.kinotic.core.api.event.Event;
import org.springframework.stereotype.Component;

/**
 * Allows writing to multiple  ChronicleQueue transparently
 *
 *
 * Created by Navid Mitchell on 11/4/20
 */
@Component
public class DefaultHFTQueueManager implements HFTQueueManager {
    @Override
    public Future<Void> write(Event<byte[]> event) {
        return null;
    }

}
