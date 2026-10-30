package org.kinotic.core.internal.api.service.invoker;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.internal.api.service.json.AbstractJacksonSupport;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.LinkedList;
import java.util.List;

/**
 * What the JSON argument resolvers share: a {@link Participant} parameter is resolved from the invocation's Vert.x
 * context, narrowed to the subtype the parameter declares, and every other parameter from the invocation's body.
 *
 * Created by Navíd Mitchell 🤪 on 10/27/26.
 */
public abstract class AbstractJacksonArgumentResolver extends AbstractJacksonSupport implements ArgumentResolver {

    private final SecurityContext securityContext;

    public AbstractJacksonArgumentResolver(JsonMapper jsonMapper,
                                           ReactiveAdapterRegistry reactiveAdapterRegistry,
                                           KinoticProperties kinoticProperties,
                                           SecurityContext securityContext) {
        super(jsonMapper, reactiveAdapterRegistry, kinoticProperties);
        this.securityContext = securityContext;
    }

    /**
     * Resolves a {@link Participant} parameter from the current Vert.x context, narrowed to the
     * {@link Participant} subtype the parameter declares.
     *
     * @param methodParameter the {@link Participant} parameter being resolved
     * @return the participant bound to the current Vert.x context
     * @throws IllegalStateException if no participant is bound to the current Vert.x context
     * @throws org.kinotic.core.api.exceptions.AuthorizationException if the participant is not of
     *         the declared subtype
     */
    protected Participant resolveParticipant(MethodParameter methodParameter) {
        // A service declares the participant scope it serves, so narrowing here turns a caller the
        // service does not serve into an authorization failure rather than an "argument type
        // mismatch" out of Method.invoke
        @SuppressWarnings("unchecked")
        Class<? extends Participant> participantType =
                (Class<? extends Participant>) methodParameter.getParameterType();
        return securityContext.requireParticipant(participantType);
    }

    /**
     * Transforms the JSON content to Java objects using the given expected parameter types
     * @param event the message containing the JSON content to be converted
     * @param parameters to determine the correct type for the {@link TokenBuffer} being decoded.
     * @param dataInArray if true the incoming data is expected to be within an array such as when decoding input arguments
     *
     * @return the deserialized JSON as Java objects
     */
    protected Object[] createJavaObjectsFromJsonEvent(Event<byte[]> event, MethodParameter[] parameters, boolean dataInArray){
        Validate.notNull(parameters, "parameters must not be null");

        List<TokenBuffer> tokens = tokenize(event, dataInArray);

        List<Object> ret = new LinkedList<>();
        int tokenCount = tokens.size();

        // Count the number of parameters that come from JSON tokens (i.e. not Participant)
        int jsonParamCount = 0;
        for (MethodParameter p : parameters) {
            if (!Participant.class.isAssignableFrom(p.nestedIfOptional().getParameterType())) {
                jsonParamCount++;
            }
        }

        if (tokenCount > jsonParamCount) {
            throw new IllegalArgumentException("Received too many json arguments, Expected: " + jsonParamCount + " Got: " + tokenCount);
        }

        int tokenIdx = 0;
        for (MethodParameter methodParameter : parameters) {

            methodParameter = methodParameter.nestedIfOptional();

            // If the parameter is a Participant we get this from the Vert.x context
            if (Participant.class.isAssignableFrom(methodParameter.getParameterType())) {

                ret.add(resolveParticipant(methodParameter));

            } else {
                if (tokenIdx >= tokenCount) {
                    throw new IllegalArgumentException("Received too few json arguments, Expected: " + jsonParamCount + " Got: " + tokenCount);
                }

                Object arg = decode(tokens.get(tokenIdx), methodParameter);
                ret.add(arg);
                tokenIdx++;
            }
        }
        return ret.toArray();
    }

}
