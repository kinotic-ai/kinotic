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

import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Resolves {@link RpcReturnValueHandler}'s by delegating to a list of {@link RpcReturnValueHandlerFactory}'s
 *
 * Created by navid on 2019-04-25.
 */
public class RpcReturnValueHandlerFactoryComposite implements RpcReturnValueHandlerFactory {

    private final List<RpcReturnValueHandlerFactory> factories = new LinkedList<>();

    public RpcReturnValueHandlerFactoryComposite addFactory(RpcReturnValueHandlerFactory factory){
        factories.add(factory);
        return this;
    }

    public RpcReturnValueHandlerFactoryComposite addFactories(RpcReturnValueHandlerFactory... factories){
        if (factories != null) {
            Collections.addAll(this.factories, factories);
        }
        return this;
    }

    public RpcReturnValueHandlerFactoryComposite addFactories(List<? extends RpcReturnValueHandlerFactory> factories){
        this.factories.addAll(factories);
        return this;
    }

    @Override
    public boolean supports(Method method) {
        return selectFactory(method) != null;
    }

    @Override
    public RpcReturnValueHandler createReturnValueHandler(Method method, Object... args) {
        RpcReturnValueHandlerFactory factory = selectFactory(method);
        Assert.notNull(factory, "Unsupported Method no ReturnValueHandlerFactory can be found. Should call supports() first.");
        return factory.createReturnValueHandler(method, args);
    }

    private RpcReturnValueHandlerFactory selectFactory(Method method){
        RpcReturnValueHandlerFactory ret = null;
        for(RpcReturnValueHandlerFactory factory : factories){
            if(factory.supports(method)){
                ret = factory;
                break;
            }
        }
        return ret;
    }

}
