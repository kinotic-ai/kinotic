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

package org.kinotic.continuum.core.api.service;

import org.kinotic.continuum.core.api.ServiceRegistry;

import java.util.Map;

/**
 * The {@link ServiceFunctionInstanceProvider} provides object instances for {@link ServiceFunction}'s
 * This allows the {@link ServiceFunction#invocationMethod()} to be invoked with the proper object instance
 *
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
public interface ServiceFunctionInstanceProvider {

    /**
     * This method will be called by the {@link ServiceRegistry}
     * for all {@link ServiceFunction}'s that are part of any registered {@link ServiceDescriptor}
     * The provider will be called when the {@link ServiceDescriptor} is registered
     *
     * @param serviceFunction that the Object instance should be provided for
     * @return the correct Object instance for the {@link ServiceFunction}
     */
    Object provideInstance(ServiceFunction serviceFunction);


    static ServiceFunctionInstanceProvider create(Object instance){
        return serviceFunction -> instance;
    }

    static ServiceFunctionInstanceProvider create(Map<ServiceFunction, Object> functionMap){
        return functionMap::get;
    }

}
