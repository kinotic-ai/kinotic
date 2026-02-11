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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Navíd Mitchell 🤪 on 9/2/21.
 */
class ReflectiveServiceDescriptor implements ServiceDescriptor{

    private static final Logger log = LoggerFactory.getLogger(ReflectiveServiceDescriptor.class);

    private final ServiceIdentifier serviceIdentifier;
    private final Collection<ServiceFunction> serviceFunctions;

    /**
     * A {@link ServiceDescriptor} created using reflection
     * @param serviceIdentifier that should be used
     * @param serviceClass the class to introspect for methods to create {@link ServiceFunction}'s for
     * @throws IllegalStateException if reflection fails
     */
    public ReflectiveServiceDescriptor(ServiceIdentifier serviceIdentifier, Class<?> serviceClass) {
        this.serviceIdentifier = serviceIdentifier;

        // build list of service functions
        Map<String, ServiceFunction> functionMap = new HashMap<>();
        ReflectionUtils.doWithMethods(serviceClass, method -> {
            String methodName = method.getName();
            if(functionMap.containsKey(methodName)){
                // in some cases such as with default methods we may actually get the same method multiple times check for that.
                if(!functionMap.get(methodName).invocationMethod().equals(method)){
                    log.warn("{} has overloaded method {} overloading is not supported. \n {} will be ignored",
                             serviceClass.getName(),
                             methodName,
                             method.toGenericString());
                }
            }else{
                functionMap.put(methodName,  ServiceFunction.create(methodName, method));
            }
        }, ReflectionUtils.USER_DECLARED_METHODS);

        this.serviceFunctions = functionMap.values();
    }

    @Override
    public ServiceIdentifier serviceIdentifier() {
        return serviceIdentifier;
    }

    @Override
    public Collection<ServiceFunction> functions() {
        return serviceFunctions;
    }
}
