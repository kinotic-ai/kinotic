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
