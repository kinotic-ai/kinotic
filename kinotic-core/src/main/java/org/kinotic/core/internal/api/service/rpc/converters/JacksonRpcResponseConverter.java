

package org.kinotic.core.internal.api.service.rpc.converters;

import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.internal.api.service.json.AbstractJacksonSupport;
import org.kinotic.core.internal.api.service.rpc.RpcResponseConverter;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.List;

/**
 *
 * Created by navid on 2019-04-23.
 */
@Component
public class JacksonRpcResponseConverter extends AbstractJacksonSupport implements RpcResponseConverter {

    public JacksonRpcResponseConverter(JsonMapper jsonMapper,
                                       ReactiveAdapterRegistry reactiveAdapterRegistry,
                                       KinoticProperties kinoticProperties) {
        super(jsonMapper, reactiveAdapterRegistry, kinoticProperties);
    }

    @Override
    public boolean supports(Event<byte[]> responseEvent, MethodParameter methodParameter) {
        return containsJsonContent(responseEvent.metadata());
    }

    @Override
    public Object convert(Event<byte[]> responseEvent, MethodParameter methodParameter) {
        Object ret = null;
        if(responseEvent.data()!= null
                && responseEvent.data().length > 0){

            Assert.notNull(methodParameter, "The return type is null but event data was found");

            List<TokenBuffer> tokens = tokenize(responseEvent, false);
            if (tokens.size() != 1) {
                throw new IllegalStateException("Event data was present but " + tokens.size() + " values were found where one reply was expected");
            }
            ret = decode(tokens.get(0), methodParameter);
        }
        return ret;
    }



}
