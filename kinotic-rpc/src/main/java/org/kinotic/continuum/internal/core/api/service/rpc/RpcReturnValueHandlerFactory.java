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
 * Creates a {@link RpcReturnValueHandler} for the given {@link java.lang.reflect.Method} and arguments
 *
 *
 * Created by navid on 2019-04-24.
 */
public interface RpcReturnValueHandlerFactory {

    /**
     * Determine if this {@link RpcReturnValueHandlerFactory} supports the given {@link Method}
     * @param method to check if supported by this {@link RpcReturnValueHandlerFactory}
     * @return true if supported false if not
     */
    boolean supports(Method method);

    /**
     * Provides the {@link RpcReturnValueHandler} for the given parameters
     * @param method the method that is being invoked by the proxy
     * @param args that were provided to the method being invoked
     * @return the new {@link RpcReturnValueHandler}
     */
    RpcReturnValueHandler createReturnValueHandler(Method method, Object... args);

}
