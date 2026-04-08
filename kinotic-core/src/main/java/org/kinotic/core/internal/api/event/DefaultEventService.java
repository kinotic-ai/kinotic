


package org.kinotic.core.internal.api.event;

import io.vertx.core.Future;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.EventService;
import org.kinotic.core.api.event.EventStreamService;
import org.springframework.stereotype.Component;

/**
 *
 * Created by navid on 12/19/19
 */
@Component
public class DefaultEventService implements EventService {

    private final EventBusService eventBusService;
    private final EventStreamService eventStreamService;

    public DefaultEventService(EventBusService eventBusService,
                               EventStreamService eventStreamService) {
        this.eventBusService = eventBusService;
        this.eventStreamService = eventStreamService;
    }

    @Override
    public Future<Void> send(Event<byte[]> event) {
        Future<Void> ret;
        if(event.cri().scheme().equals(EventConstants.SERVICE_DESTINATION_SCHEME)){
            ret = eventBusService.sendWithAck(event);
        }else if(event.cri().scheme().equals(EventConstants.STREAM_DESTINATION_SCHEME)){
            ret = eventStreamService.send(event);
        }else{
            throw new IllegalArgumentException("Event cri must begin with "
                                                       + EventConstants.SERVICE_DESTINATION_SCHEME
                                                       + " or "
                                                       + EventConstants.STREAM_DESTINATION_SCHEME);
        }
        return ret;
    }

    @Override
    public EventConsumer listen(String cri) {
        EventConsumer ret;
        if(cri.startsWith(EventConstants.SERVICE_DESTINATION_SCHEME)){
            ret = eventBusService.listen(cri);
        }else if(cri.startsWith(EventConstants.STREAM_DESTINATION_SCHEME)){
            ret = eventStreamService.listen(CRI.create(cri));
        }else{
            throw new IllegalArgumentException("Event cri must begin with "
                                                       + EventConstants.SERVICE_DESTINATION_SCHEME
                                                       + " or "
                                                       + EventConstants.STREAM_DESTINATION_SCHEME);
        }
        return ret;
    }
}
