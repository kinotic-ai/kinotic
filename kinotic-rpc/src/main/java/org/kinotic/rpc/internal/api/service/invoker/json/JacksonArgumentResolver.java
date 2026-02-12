

package org.kinotic.rpc.internal.api.service.invoker.json;

import org.kinotic.rpc.api.config.ContinuumProperties;
import org.kinotic.rpc.api.event.Event;
import org.kinotic.rpc.internal.api.service.invoker.ArgumentResolver;
import org.kinotic.rpc.internal.api.service.invoker.HandlerMethod;
import org.kinotic.rpc.internal.api.service.json.AbstractJacksonSupport;
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
                                   ContinuumProperties continuumProperties) {
        super(jsonMapper, reactiveAdapterRegistry, continuumProperties);
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
