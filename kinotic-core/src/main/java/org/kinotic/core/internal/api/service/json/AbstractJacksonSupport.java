

package org.kinotic.core.internal.api.service.json;


import io.vertx.core.Context;
import io.vertx.core.Vertx;
import lombok.Getter;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.internal.api.service.invoker.ServiceInvocationSupervisor;
import org.kinotic.core.internal.config.KinoticVertxConfig;
import org.kinotic.core.internal.utils.EventUtil;
import org.apache.commons.lang3.Validate;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.EncodingException;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.databind.util.TokenBuffer;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by Navid Mitchell on 2019-04-08.
 */
public abstract class AbstractJacksonSupport {

    @Getter
    private final JsonMapper jsonMapper;
    private final ReactiveAdapterRegistry reactiveAdapterRegistry;
    private final KinoticProperties kinoticProperties;

    public AbstractJacksonSupport(JsonMapper jsonMapper,
                                  ReactiveAdapterRegistry reactiveAdapterRegistry,
                                  KinoticProperties kinoticProperties) {
        this.jsonMapper = jsonMapper;
        this.reactiveAdapterRegistry = reactiveAdapterRegistry;
        this.kinoticProperties = kinoticProperties;

    }

    /**
     * Tests if the content is considered JSON
     * @param incomingMetadata to evaluate
     * @return true if the content-type header of the message is application/json
     */
    protected boolean containsJsonContent(Metadata incomingMetadata) {
        boolean ret = false;
        String contentType = incomingMetadata.get(EventConstants.CONTENT_TYPE_HEADER);
        if(contentType != null && !contentType.isEmpty()){
            ret =  MimeTypeUtils.APPLICATION_JSON_VALUE.contentEquals(contentType);
        }
        return ret;
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
        Validate.notNull(event, "event must not be null");
        Validate.notNull(parameters, "parameters must not be null");

        // TODO: remove the use of the Spring Tokenizer since I have found out about the performance issues with reactor
        // Should we use the JacksonTokenizer borrowed from spring? We are not really taking advantage of the claimed non blocking or the Flux themselves
        // I really don't see a way to do that anyhow since all Arguments at least for the invoker must be available upfront.
        // A return value could be parsed and streamed but that would require more machinery.
        // And we would want to plum that all the way to the caller.
        List<TokenBuffer> tokens = JacksonTokenizer.tokenize(Flux.just(event.data()),
                                                             jsonMapper,
                                                             dataInArray,
                                                             kinoticProperties.getMaxEventPayloadSize())
                                                   .collectList()
                                                   .block();

        List<Object> ret = new LinkedList<>();
        if(tokens!= null && !tokens.isEmpty()){

            if(tokens.size() > parameters.length){
                // Error could be misleading / inaccurate, Should we keep the number of participant args in mind?
                throw new IllegalArgumentException("Received too many json arguments, Expected: " + parameters.length + " Got: " +tokens.size());
            }

            int tokenIdx = 0;
            for(MethodParameter methodParameter: parameters){

                methodParameter = methodParameter.nestedIfOptional();

                // If the parameter is a Participant we get this from the Vert.x context
                if(Participant.class.isAssignableFrom(methodParameter.getParameterType())){

                    Context context = Vertx.currentContext();
                    Participant participant = context != null ? context.getLocal(KinoticVertxConfig.PARTICIPANT_LOCAL) : null;
                    if(participant != null){
                        ret.add(participant);
                    }else{
                        throw new IllegalArgumentException("Participant parameter is required but no Participant is available in the Vert.x context");
                    }

                }else{
                    if(tokenIdx + 1 > tokens.size()){ // index is zero base..
                        // Error could be misleading / inaccurate, Should we keep the number of participant args in mind?
                        throw new IllegalArgumentException("Received too few json arguments, Expected: " + parameters.length + " Got: " +tokens.size());
                    }

                    Object arg = decodeInternal(tokens.get(tokenIdx), methodParameter);
                    ret.add(arg);
                    tokenIdx++;
                }
            }
        }
        return ret.toArray();
    }

    private Object decodeInternal(TokenBuffer tokenBuffer, MethodParameter methodParameter){
        // Unwrap async classes, this is also used for method return values so this handles that..
        if(reactiveAdapterRegistry.getAdapter(methodParameter.getParameterType()) != null){
            methodParameter = methodParameter.nested();
        }

        Object ret;

        // The parser will return null for void so we don't parse void
        if(!Void.class.isAssignableFrom(methodParameter.getParameterType())){

            // Support passing the TokenBuffer directly
            if (TokenBuffer.class.isAssignableFrom(methodParameter.getParameterType())) {

                ret = tokenBuffer;

            } else {

                JavaType javaType = getJavaType(methodParameter);
                ObjectReader reader = getJsonMapper().readerFor(javaType);

                try {

                    ret = reader.readValue(tokenBuffer.asParser(getJsonMapper()._deserializationContext()));

                } catch (InvalidDefinitionException ex) {
                    throw new CodecException("Type definition error: " + ex.getType(), ex);
                } catch (JacksonException ex) {
                    throw new DecodingException("JSON decoding error: " + ex.getOriginalMessage(), ex);
                }
            }
        }else{
            ret = Void.TYPE;
        }

        return ret;
    }

    JavaType getJavaType(MethodParameter methodParameter){
        Type targetType = methodParameter.getNestedGenericParameterType();
        Class<?> contextClass = methodParameter.getContainingClass();
        TypeFactory typeFactory = this.jsonMapper.getTypeFactory();
        return typeFactory.constructType(GenericTypeResolver.resolveType(targetType, contextClass));
    }

    /**
     * Creates a {@link Event} that can be sent based on the incomingMessage headers and the data to use as the body
     * @param incomingMetadata the original {@link Metadata} sent to the {@link ServiceInvocationSupervisor}
     * @param headers key value pairs that will be added to the outgoing headers
     * @param body the value that will be converted to a JSON string and set as the body
     * @return the {@link Event} to send
     */
    protected Event<byte[]> createOutgoingEvent(Metadata incomingMetadata, Map<String, String> headers, Object body){
        return EventUtil.createReplyEvent(incomingMetadata, headers, () -> {
            byte[] jsonBytes;
            try {

                jsonBytes = jsonMapper.writeValueAsBytes(body);

            } catch (JacksonException e) {
                throw new EncodingException("JSON encoding error: " + e.getOriginalMessage(), e);
            }
            return jsonBytes;
        });
    }


}
