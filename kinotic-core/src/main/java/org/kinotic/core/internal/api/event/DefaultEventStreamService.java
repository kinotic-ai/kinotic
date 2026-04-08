


package org.kinotic.core.internal.api.event;

import io.vertx.core.Future;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.EventStreamService;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;

/**
 *
 * Created by navid on 10/23/19
 */
// FIXME: The built in service should use the Vertx event bus
@Component
public class DefaultEventStreamService implements EventStreamService {
    @Override
    public Future<Void> send(Event<byte[]> event) {
        return null;
    }

    @Override
    public Future<Void> sendStream(Publisher<Event<byte[]>> publisher) {
        return null;
    }

    @Override
    public EventConsumer listen(CRI cri) {
        return null;
    }

}
