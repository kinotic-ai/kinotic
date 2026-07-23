package org.kinotic.core.internal.api.service.invoker;

import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.internal.api.service.json.AbstractJacksonSupport;
import org.kinotic.idl.api.utils.IdlUtil;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Resolves arguments from a {@link EventConstants#CONTENT_TYPE_NAMED_JSON} body: a single JSON object whose
 * fields bind to the invoked method's parameters by name.
 */
@Component
public class NamedJsonArgumentResolver extends AbstractJacksonSupport implements ArgumentResolver {

    private final SecurityContext securityContext;

    public NamedJsonArgumentResolver(JsonMapper jsonMapper,
                                     ReactiveAdapterRegistry reactiveAdapterRegistry,
                                     KinoticProperties kinoticProperties,
                                     SecurityContext securityContext) {
        super(jsonMapper, reactiveAdapterRegistry, kinoticProperties, securityContext);
        this.securityContext = securityContext;
    }

    @Override
    public Object[] resolveArguments(Event<byte[]> incomingEvent, HandlerMethod handlerMethod) {
        ObjectNode body = readBody(incomingEvent);

        List<Object> ret = new LinkedList<>();
        Set<String> parameterNames = new HashSet<>();
        for (MethodParameter methodParameter : handlerMethod.getMethodParameters()) {

            methodParameter = methodParameter.nestedIfOptional();

            // A Participant parameter comes from the Vert.x context, never from the body
            if (Participant.class.isAssignableFrom(methodParameter.getParameterType())) {
                Participant participant = securityContext.currentParticipant();
                if (participant == null) {
                    throw new IllegalArgumentException("Participant parameter is required but no Participant is available in the Vert.x context");
                }
                ret.add(participant);
            } else {
                // IdlUtil.parameterName is the shared naming rule, so the name bound here is
                // the name the emitted schema declared
                String parameterName = IdlUtil.parameterName(methodParameter);
                parameterNames.add(parameterName);
                JsonNode value = body.get(parameterName);
                ret.add(value == null || value.isNull() ? null : convert(value, methodParameter));
            }
        }

        for (String fieldName : body.propertyNames()) {
            if (!parameterNames.contains(fieldName)) {
                throw new IllegalArgumentException("Received argument '" + fieldName + "' which matches no parameter of "
                                                           + handlerMethod.getMethod().getName());
            }
        }
        return ret.toArray();
    }

    @Override
    public boolean supports(Event<byte[]> incomingEvent) {
        return EventConstants.CONTENT_TYPE_NAMED_JSON.equals(incomingEvent.metadata().get(EventConstants.CONTENT_TYPE_HEADER));
    }

    private ObjectNode readBody(Event<byte[]> incomingEvent) {
        byte[] data = incomingEvent.data();
        if (data == null || data.length == 0) {
            return getJsonMapper().createObjectNode();
        }
        JsonNode parsed;
        try {
            parsed = getJsonMapper().readTree(data);
        } catch (JacksonException e) {
            throw new DecodingException("JSON decoding error: " + e.getOriginalMessage(), e);
        }
        if (!(parsed instanceof ObjectNode objectNode)) {
            throw new IllegalArgumentException("A " + EventConstants.CONTENT_TYPE_NAMED_JSON
                                                       + " body must be a JSON object but was " + parsed.getNodeType());
        }
        return objectNode;
    }

    private Object convert(JsonNode value, MethodParameter methodParameter) {
        try {
            return getJsonMapper().readerFor(getJavaType(methodParameter)).readValue(value);
        } catch (JacksonException e) {
            throw new DecodingException("JSON decoding error: " + e.getOriginalMessage(), e);
        }
    }
}
