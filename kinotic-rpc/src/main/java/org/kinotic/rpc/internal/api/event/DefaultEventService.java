

package org.kinotic.rpc.internal.api.event;

import org.kinotic.rpc.api.event.CRI;
import org.kinotic.rpc.api.event.Event;
import org.kinotic.rpc.api.event.EventBusService;
import org.kinotic.rpc.api.event.EventConstants;
import org.kinotic.rpc.api.event.EventService;
import org.kinotic.rpc.api.event.EventStreamService;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public Mono<Void> send(Event<byte[]> event) {
        Mono<Void> ret;
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
    public Flux<Event<byte[]>> listen(String cri) {
        Flux<Event<byte[]>> ret;
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
