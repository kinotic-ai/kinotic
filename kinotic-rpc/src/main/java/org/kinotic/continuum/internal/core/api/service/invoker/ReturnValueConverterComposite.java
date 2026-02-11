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

package org.kinotic.continuum.internal.core.api.service.invoker;

import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.Metadata;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Resolves return value by delegating to a list of {@link ReturnValueConverter}'s
 *
 *
 * Created by Navid Mitchell on 2019-03-30.
 */
public class ReturnValueConverterComposite implements ReturnValueConverter {

    private final List<ReturnValueConverter> converters = new LinkedList<>();

    public ReturnValueConverterComposite addConverter(ReturnValueConverter converter){
        converters.add(converter);
        return this;
    }

    public ReturnValueConverterComposite addConverters(ReturnValueConverter... converters){
        if (converters != null) {
            Collections.addAll(this.converters, converters);
        }
        return this;
    }

    public ReturnValueConverterComposite addConverters(List<? extends ReturnValueConverter> converters){
        this.converters.addAll(converters);
        return this;
    }

    @Override
    public Event<byte[]> convert(Metadata incomingMetadata, Class<?> returnType, Object returnValue) {
        ReturnValueConverter converter = selectConverter(incomingMetadata, returnType);
        Assert.notNull(converter,"Unsupported Return Value no ReturnValueConverter can be found. Should call supports() first.");
        return converter.convert(incomingMetadata, returnType, returnValue);
    }

    @Override
    public boolean supports(Metadata incomingMetadata, Class<?> returnType) {
        return selectConverter(incomingMetadata, returnType) != null;
    }

    private ReturnValueConverter selectConverter(Metadata incomingMetadata, Class<?> returnType){
        ReturnValueConverter ret = null;
        for(ReturnValueConverter converter : converters){
            if(converter.supports(incomingMetadata, returnType)){
                ret = converter;
                break;
            }
        }
        return ret;
    }

}
