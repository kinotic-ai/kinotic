/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.internal.core.api.service.json;


import lombok.Getter;
import org.kinotic.continuum.api.config.ContinuumProperties;
import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.EventConstants;
import org.kinotic.continuum.core.api.event.Metadata;
import org.kinotic.continuum.api.security.Participant;
import org.kinotic.continuum.internal.core.api.service.invoker.ServiceInvocationSupervisor;
import org.kinotic.continuum.internal.utils.EventUtil;
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
    private final ContinuumProperties continuumProperties;

    public AbstractJacksonSupport(JsonMapper jsonMapper,
                                  ReactiveAdapterRegistry reactiveAdapterRegistry,
                                  ContinuumProperties continuumProperties) {
        this.jsonMapper = jsonMapper;
        this.reactiveAdapterRegistry = reactiveAdapterRegistry;
        this.continuumProperties = continuumProperties;

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
                                                             continuumProperties.getMaxEventPayloadSize())
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

                // FIXME: when the invocation is local this happens for no reason. If the event stays on the local bus we shouldn't do this..
                // if the parameter is a participant we get this from the even metadata
                if(Participant.class.isAssignableFrom(methodParameter.getParameterType())){

                    String participantJson = event.metadata().get(EventConstants.SENDER_HEADER);

                    if(participantJson != null){
                        try {
                            Participant participant = jsonMapper.readValue(participantJson, Participant.class);
                            ret.add(participant);
                        } catch (JacksonException e) {
                            throw new DecodingException("JSON decoding error: " + e.getOriginalMessage(), e);
                        }
                    }else{
                        throw new IllegalArgumentException("Participant parameter is required but no Participant is available");
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
