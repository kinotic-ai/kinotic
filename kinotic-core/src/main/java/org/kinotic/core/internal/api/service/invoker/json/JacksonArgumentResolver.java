

package org.kinotic.core.internal.api.service.invoker.json;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.spi.context.storage.ContextLocal;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.internal.api.service.invoker.ArgumentResolver;
import org.kinotic.core.internal.api.service.invoker.HandlerMethod;
import org.kinotic.core.internal.api.service.json.AbstractJacksonSupport;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves arguments from JSON data using jackson
 * Created by Navid Mitchell on 2019-04-08.
 */
@Slf4j
@Component
public class JacksonArgumentResolver extends AbstractJacksonSupport implements ArgumentResolver {

    public static final ContextLocal<Participant> PARTICIPANT_LOCAL = ContextLocal.registerLocal(Participant.class);

    public JacksonArgumentResolver(JsonMapper jsonMapper,
                                   ReactiveAdapterRegistry reactiveAdapterRegistry,
                                   KinoticProperties kinoticProperties) {
        super(jsonMapper, reactiveAdapterRegistry, kinoticProperties);
    }

    @Override
    public Object[] resolveArguments(Event<byte[]> incomingEvent, HandlerMethod handlerMethod) {
        // Inject the Participant into the Vert.x context so service methods can access it via context.getLocal()
        String participantJson = incomingEvent.metadata().get(EventConstants.SENDER_HEADER);
        if (participantJson != null) {
            try {
                Participant participant = getJsonMapper().readValue(participantJson, Participant.class);
                Context context = Vertx.currentContext();
                if (context != null) {
                    context.putLocal(PARTICIPANT_LOCAL, participant);
                }
            } catch (JacksonException e) {
                log.warn("Failed to deserialize Participant from event metadata", e);
            }
        }

        return createJavaObjectsFromJsonEvent(incomingEvent, handlerMethod.getMethodParameters(), true);
    }

    @Override
    public boolean supports(Event<byte[]> incomingEvent) {
        return containsJsonContent(incomingEvent.metadata());
    }

}
