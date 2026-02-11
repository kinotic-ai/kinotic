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

package org.kinotic.continuum.internal.core.api.service.rpc;

import org.kinotic.continuum.core.api.event.Event;
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
