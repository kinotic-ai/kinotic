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

package org.kinotic.continuum.internal.core.api.service.rpc.converters;

import org.kinotic.continuum.api.config.ContinuumProperties;
import org.kinotic.continuum.internal.core.api.service.json.AbstractJacksonSupport;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcArgumentConverter;
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
