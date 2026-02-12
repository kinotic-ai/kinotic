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

package org.kinotic.rpc.api;


/**
 * Contains information about this Continuum process
 *
 * Created by navid on 9/24/19
 */
public interface Continuum {

    /**
     * Returns information about this Continuum server.
     * @return the {@link ServerInfo} object
     */
    ServerInfo serverInfo();

    /**
     * Returns the name of the application.
     * This comes from the class annotated with {@link EnableContinuum}
     * @return the name of the application
     */
    String applicationName();

    /**
     * Returns the version of the application
     * This comes from the class annotated with {@link EnableContinuum}
     * @return the version of the application
     */
    String applicationVersion();

}
