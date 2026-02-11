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

package org.kinotic.continuum.internal.core.api.service.rpc.types;

import io.vertx.core.Future;
import org.kinotic.continuum.internal.core.api.service.ExceptionConverter;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcResponseConverter;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcReturnValueHandler;
import org.kinotic.continuum.internal.core.api.service.rpc.RpcReturnValueHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 *
 * Created by navid on 2019-04-25.
 */
@Component
public class VertxFutureRpcReturnValueHandlerFactory implements RpcReturnValueHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(VertxFutureRpcReturnValueHandlerFactory.class);

    private final RpcResponseConverter rpcResponseConverter;
    private final ExceptionConverter exceptionConverter;

    public VertxFutureRpcReturnValueHandlerFactory(RpcResponseConverter rpcResponseConverter,
                                                   ExceptionConverter exceptionConverter) {
        this.rpcResponseConverter = rpcResponseConverter;
        this.exceptionConverter = exceptionConverter;
    }

    @Override
    public boolean supports(Method method) {
        boolean ret = false;
        if(method.getReturnType().isAssignableFrom(Future.class)){
            if(GenericTypeResolver.resolveReturnTypeArgument(method, Future.class) != null){
                ret = true;
            }else{
                log.warn("io.vertx.core.Future is only supported if a generic parameter is provided.\nIf a void return value is desired use Future<Void> for the method definition.");
            }
        }
        return ret;
    }

    @Override
    public RpcReturnValueHandler createReturnValueHandler(Method method, Object... args) {
        return new VertxFutureRpcReturnValueHandler(new MethodParameter(method, -1),
                                                    rpcResponseConverter,
                                                    exceptionConverter);
    }

}
