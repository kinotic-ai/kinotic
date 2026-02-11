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
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Resolves arguments by delegating to a list of {@link ArgumentResolver}'s
 *
 *
 * Created by Navid Mitchell on 2019-03-29.
 */
public class ArgumentResolverComposite implements ArgumentResolver {

    private final List<ArgumentResolver> resolvers = new LinkedList<>();

    public ArgumentResolverComposite addResolver(ArgumentResolver resolver){
        resolvers.add(resolver);
        return this;
    }

    public ArgumentResolverComposite addResolvers(ArgumentResolver... resolvers){
        if (resolvers != null) {
            Collections.addAll(this.resolvers, resolvers);
        }
        return this;
    }

    public ArgumentResolverComposite addResolvers(List<? extends ArgumentResolver> resolvers){
        this.resolvers.addAll(resolvers);
        return this;
    }

    @Override
    public Object[] resolveArguments(Event<byte[]> incomingEvent, HandlerMethod handlerMethod) {
        ArgumentResolver resolver = selectResolver(incomingEvent);
        Assert.notNull(resolver,"Unsupported Message content no parameter resolver can be found. Should call supports() first.");
        return resolver.resolveArguments(incomingEvent, handlerMethod);
    }

    @Override
    public boolean supports(Event<byte[]> incomingEvent) {
        return selectResolver(incomingEvent) != null;
    }

    private ArgumentResolver selectResolver(Event<byte[]> message){
        ArgumentResolver ret = null;
        for(ArgumentResolver resolver : resolvers){
            if(resolver.supports(message)){
                ret = resolver;
                break;
            }
        }
        return ret;
    }

}
