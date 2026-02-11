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

/**
 *
 * Created by Navid Mitchell on 6/23/20
 */
public interface RpcArgumentConverterResolver {

    /**
     * Determines if a valid {@link RpcArgumentConverter} can be resolved for the given contentType
     * @param contentType contentType that the must be produced by the resolved {@link RpcArgumentConverter}
     * @return true if there is a valid {@link RpcArgumentConverter} for the contentType false if not
     */
    boolean canResolve(String contentType);

    /**
     * Resolve the correct {@link RpcArgumentConverter} for the desired contentType
     * @param contentType that the must be produced by the resolved {@link RpcArgumentConverter}
     * @return the correct {@link RpcArgumentConverter} for the content type
     */
    RpcArgumentConverter resolve(String contentType);

}
