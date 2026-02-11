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

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
class DefaultServiceDescriptor implements ServiceDescriptor{

    private final ServiceIdentifier serviceIdentifier;
    private final Collection<ServiceFunction> serviceFunctions;

    public DefaultServiceDescriptor(ServiceIdentifier serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
        this.serviceFunctions = new ArrayList<>();
    }

    public DefaultServiceDescriptor(ServiceIdentifier serviceIdentifier,
                                    Collection<ServiceFunction> serviceFunctions) {
        this.serviceIdentifier = serviceIdentifier;
        this.serviceFunctions = serviceFunctions;
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
