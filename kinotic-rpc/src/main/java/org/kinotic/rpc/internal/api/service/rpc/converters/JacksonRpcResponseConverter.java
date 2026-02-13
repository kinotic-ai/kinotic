

package org.kinotic.rpc.internal.api.service.rpc.converters;

import org.kinotic.boot.api.config.KinoticProperties;
import org.kinotic.rpc.api.event.Event;
import org.kinotic.rpc.internal.api.service.json.AbstractJacksonSupport;
import org.kinotic.rpc.internal.api.service.rpc.RpcResponseConverter;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;

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

            Object[] temp = createJavaObjectsFromJsonEvent(responseEvent, new MethodParameter[]{methodParameter}, false);

            // We know no more than a single response can be returned because of check above
            // however we want to verify we got at least one
            if(temp.length == 1){

                ret = temp[0];

            }else if(temp.length == 0){
                throw new IllegalStateException("Event data was present but no values could be converted");
            }
        }
        return ret;
    }



}
