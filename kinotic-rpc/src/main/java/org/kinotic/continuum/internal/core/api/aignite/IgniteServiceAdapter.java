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

package org.kinotic.continuum.internal.core.api.aignite;

import org.apache.commons.lang3.Validate;
import org.apache.ignite.resources.SpringApplicationContextResource;
import org.apache.ignite.services.Service;
import org.apache.ignite.services.ServiceContext;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;

/**
 * Provides functionality to deploy an Ignite {@link Service} that configures a new Class instance using spring
 *
 * Created by navid on 10/15/19
 */
public class IgniteServiceAdapter implements Service {

    private final String serviceIdentifier;
    private final Class<?> serviceClass;
    private final Object[] constructorArgs;
    private Object serviceInstance;

    @SpringApplicationContextResource
    protected ApplicationContext applicationContext;

    public IgniteServiceAdapter(String serviceIdentifier,
                                Class<?> serviceClass,
                                Object[] constructorArgs) {
        Validate.notBlank(serviceIdentifier,"The serviceIdentifier provided must not be blank");
        Validate.notNull(serviceClass, "The service class provided must not be null");
        this.serviceIdentifier = serviceIdentifier;
        this.serviceClass = serviceClass;
        this.constructorArgs = constructorArgs;
    }

    @Override
    public void cancel(ServiceContext ctx) {
        // destroy instance using Spring Goodness!!
        if(serviceInstance != null) {
            applicationContext.getAutowireCapableBeanFactory().destroyBean(serviceInstance);
            serviceInstance = null;
        }
    }

    @Override
    public void init(ServiceContext ctx) throws Exception {
        // Create a new instance of the desired class using the arguments provided
        Class<?>[] argTypes = Arrays.stream(constructorArgs)
                                    .map(Object::getClass)
                                    .toArray(Class[]::new);

        Object instance = serviceClass.getDeclaredConstructor(argTypes).newInstance(constructorArgs);

        // Now wire instance and initialize it using Spring Goodness!!
        applicationContext.getAutowireCapableBeanFactory().autowireBean(instance);
        serviceInstance = applicationContext.getAutowireCapableBeanFactory().initializeBean(instance, serviceIdentifier);
    }

    @Override
    public void execute(ServiceContext ctx) throws Exception {
        Thread.sleep(300000);
    }

}
