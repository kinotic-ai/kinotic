

package org.kinotic.core.internal.api.service.invoker.json;

import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.security.ParticipantContext;
import org.kinotic.core.internal.api.service.invoker.ArgumentResolver;
import org.kinotic.core.internal.api.service.invoker.HandlerMethod;
import org.kinotic.core.internal.api.service.json.AbstractJacksonSupport;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves arguments from JSON data using jackson
 * Created by Navid Mitchell on 2019-04-08.
 */
@Component
public class JacksonArgumentResolver extends AbstractJacksonSupport implements ArgumentResolver {

    public JacksonArgumentResolver(JsonMapper jsonMapper,
                                   ReactiveAdapterRegistry reactiveAdapterRegistry,
                                   KinoticProperties kinoticProperties,
                                   ParticipantContext participantContext) {
        super(jsonMapper, reactiveAdapterRegistry, kinoticProperties, participantContext);
    }

    @Override
    public Object[] resolveArguments(Event<byte[]> incomingEvent, HandlerMethod handlerMethod) {
        return createJavaObjectsFromJsonEvent(incomingEvent, handlerMethod.getMethodParameters(), true);
    }

    @Override
    public boolean supports(Event<byte[]> incomingEvent) {
        return containsJsonContent(incomingEvent.metadata());
    }

}
