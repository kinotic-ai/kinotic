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

import java.lang.reflect.Method;

/**
 * Created by Navíd Mitchell 🤪 on 7/18/21.
 */
public interface ServiceFunction {

    /**
     * The name of this {@link ServiceFunction}
     * @return string containing the name
     */
    String name();

    /**
     * The method that can be invoked for this {@link ServiceFunction}
     * @return the method to invoke
     */
    Method invocationMethod();

    static ServiceFunction create(String name, Method invocationMethod){
        return new DefaultServiceFunction(name, invocationMethod);
    }

}
