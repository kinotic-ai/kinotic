

package org.kinotic.rpc.internal.api.service.rpc;

import org.kinotic.rpc.api.event.Event;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Created by Navid Mitchell on 6/23/20
 */
public class RpcResponseConverterComposite implements RpcResponseConverter {

    private final List<RpcResponseConverter> converters = new LinkedList<>();

    public RpcResponseConverterComposite addConverter(RpcResponseConverter converter){
        converters.add(converter);
        return this;
    }

    public RpcResponseConverterComposite addConverters(RpcResponseConverter... converters){
        if (converters != null) {
            Collections.addAll(this.converters, converters);
        }
        return this;
    }

    public RpcResponseConverterComposite addConverters(List<? extends RpcResponseConverter> converters){
        this.converters.addAll(converters);
        return this;
    }

    @Override
    public boolean supports(Event<byte[]> responseEvent, MethodParameter methodParameter) {
        return selectConverter(responseEvent, methodParameter) != null;
    }

    @Override
    public Object convert(Event<byte[]> responseEvent, MethodParameter methodParameter) {
        RpcResponseConverter converter = selectConverter(responseEvent, methodParameter);
        Assert.notNull(converter, "Unsupported Response Event no RpcResponseConverter can be found. Should call supports() first.");
        return converter.convert(responseEvent, methodParameter);
    }

    private RpcResponseConverter selectConverter(Event<byte[]> responseEvent, MethodParameter methodParameter){
        RpcResponseConverter ret = null;
        for(RpcResponseConverter converter : converters){
            if(converter.supports(responseEvent, methodParameter)){
                ret = converter;
                break;
            }
        }
        return ret;
    }
}
