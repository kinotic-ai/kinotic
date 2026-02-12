

package org.kinotic.rpc.internal.api.service.rpc;

import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * Created by Navid Mitchell on 6/23/20
 */
@Component
public class DefaultRpcArgumentConverterResolver implements RpcArgumentConverterResolver {

    private final Map<String, RpcArgumentConverter> converterMap;

    public DefaultRpcArgumentConverterResolver(List<RpcArgumentConverter> converters) {
        this.converterMap = new HashMap<>(converters.size());
        for(RpcArgumentConverter converter: converters){
            this.converterMap.put(converter.producesContentType(), converter);
        }
    }

    @Override
    public boolean canResolve(String contentType) {
        return converterMap.containsKey(contentType);
    }

    @Override
    public RpcArgumentConverter resolve(String contentType) {
        RpcArgumentConverter converter = converterMap.get(contentType);
        Validate.notNull(converter, "There is no valid RpcArgumentConverter for the contentType:"+contentType+". You should call canResolve first.");
        return converter;
    }
}
