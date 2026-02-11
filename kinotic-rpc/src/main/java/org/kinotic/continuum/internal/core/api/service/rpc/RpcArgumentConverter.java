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

import java.lang.reflect.Method;

/**
 * Converts arguments that are passed to a service proxy.
 *
 *
 * Created by navid on 2019-04-19.
 */
public interface RpcArgumentConverter {

    /**
     * @return a string with the mime type for the content type produced by this converters convert method ex: application/json
     */
    String producesContentType();

    /**
     * Converts the arguments into a Buffer that can be sent across the event bus.
     * @param method that is being invoked
     * @param args the arguments passed to the invoked method
     * @return a byte[] with the data to be sent
     */
    byte[] convert(Method method, Object[] args);

}
