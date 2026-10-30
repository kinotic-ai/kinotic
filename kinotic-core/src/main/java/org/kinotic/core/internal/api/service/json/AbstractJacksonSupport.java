

package org.kinotic.core.internal.api.service.json;


import lombok.Getter;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.Metadata;
import org.kinotic.core.internal.api.service.invoker.ServiceInvocationSupervisor;
import org.kinotic.core.internal.utils.EventUtil;
import org.apache.commons.lang3.Validate;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.EncodingException;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.databind.util.TokenBuffer;

import java.lang.reflect.Type;
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
     * Splits the JSON content of the event into one {@link TokenBuffer} per value.
     * @param event the message containing the JSON content
     * @param dataInArray if true the content is an array holding one value per element, such as input arguments
     * @return the values' tokens, bounded by {@link KinoticProperties#getMaxEventPayloadSize()}
     */
    protected List<TokenBuffer> tokenize(Event<byte[]> event, boolean dataInArray){
        Validate.notNull(event, "event must not be null");
        return JacksonTokenizer.tokenize(event.data(),
                                         jsonMapper,
                                         dataInArray,
                                         kinoticProperties.getMaxEventPayloadSize());
    }

    /**
     * Decodes one value into the parameter's type, unwrapping a reactive type to the value it carries.
     * @param tokenBuffer the value's tokens
     * @param methodParameter the parameter, or return type, the value is decoded for
     * @return the decoded value, the {@link TokenBuffer} itself for a parameter of that type, or {@link Void#TYPE}
     *         for a void parameter
     */
    protected Object decode(TokenBuffer tokenBuffer, MethodParameter methodParameter){
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

    protected JavaType getJavaType(MethodParameter methodParameter){
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
