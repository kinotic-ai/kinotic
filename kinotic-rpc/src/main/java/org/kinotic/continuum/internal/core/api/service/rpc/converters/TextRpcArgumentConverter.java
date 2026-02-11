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

import org.kinotic.continuum.internal.core.api.service.rpc.RpcArgumentConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.lang.reflect.Method;

/**
 *
 * Created by Navid Mitchell on 6/24/20
 */
@Component
public class TextRpcArgumentConverter implements RpcArgumentConverter {

    @Override
    public String producesContentType() {
        return MimeTypeUtils.TEXT_PLAIN_VALUE;
    }

    @Override
    public byte[] convert(Method method, Object[] args) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < args.length; i++){
            if(i > 0){
                sb.append("\n");
            }
            sb.append(args[i]);
        }
        return sb.toString().getBytes();
    }
}
