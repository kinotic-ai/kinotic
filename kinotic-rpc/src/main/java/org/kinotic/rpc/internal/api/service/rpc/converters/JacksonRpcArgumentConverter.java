

package org.kinotic.rpc.internal.api.service.rpc.converters;

import org.kinotic.rpc.api.config.ContinuumProperties;
import org.kinotic.rpc.internal.api.service.json.AbstractJacksonSupport;
import org.kinotic.rpc.internal.api.service.rpc.RpcArgumentConverter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.codec.EncodingException;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;

/**
 *
 * Created by navid on 2019-04-23.
 */
@Component
public class JacksonRpcArgumentConverter extends AbstractJacksonSupport implements RpcArgumentConverter {

    public JacksonRpcArgumentConverter(JsonMapper jsonMapper,
                                       ReactiveAdapterRegistry reactiveAdapterRegistry,
                                       ContinuumProperties continuumProperties) {
        super(jsonMapper, reactiveAdapterRegistry, continuumProperties);
    }

    @Override
    public String producesContentType() {
        return MimeTypeUtils.APPLICATION_JSON_VALUE;
    }

    @Override
    public byte[] convert(Method method, Object[] args) {
        byte[] ret;

        if(args != null && args.length > 0){
            try {

                ret = getJsonMapper().writeValueAsBytes(args);

            } catch (JacksonException e) {
                throw new EncodingException("JSON encoding error: " + e.getOriginalMessage(), e);
            }
        }else{
            ret = new byte[0];
        }

        return ret;
    }

}
