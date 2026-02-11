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

package org.kinotic.continuum.api.config;

/**
 *
 * Created by Navíd Mitchell 🤪 on 1/18/21
 */
public interface ContinuumProperties {

    /**
     * @return the work directory for ignite
     */
    String getIgniteWorkDirectory();

    /**
     * If true additional information will be provided to clients,
     * including server information, and information about errors occurring when invoking services
     * This is off by default since this could reveal server implementation details
     * @return true to enable debug mode false to disable it
     */
    boolean isDebug();

    /**
     * @return true if clustering should be disabled false if not
     */
    boolean isDisableClustering();

    /**
     * @return the port to use when clustering = 0 (meaning assign a random port)
     */
    int getEventBusClusterPort();

    /**
     * @return The cluster public port or -1 which means use the same as the cluster port.
     */
    int getEventBusClusterPublicPort();

    /**
     * Sets the host. Defaults to null.
     * When the clustered eventbus starts, it tries to bind to the provided host.
     * If host is null, then it tries to bind to the same host as the underlying cluster manager.
     * As a last resort, an address will be picked among the available network interfaces.
     * @return The cluster host or null which means use the same as the cluster manager, if possible.
     */
    String getEventBusClusterHost();

    /**
     * The public facing hostname to be used for clustering.
     * Sometimes, e.g. when running on certain clouds, the local address the server listens on for clustering is not the same address that other nodes connect to it at, as the OS / cloud infrastructure does some kind of proxying.
     * If this is the case you can specify a public hostname which is different from the hostname the server listens at.
     * The default value is null which means use the same as the cluster hostname.
     * @return the cluster public host or null which means use the same as the cluster host.
     */
    String getEventBusClusterPublicHost();

    long getSessionTimeout();

    long getMaxOffHeapMemory();

    int getMaxEventPayloadSize();

    /**
     * The maximum number of CPU cores if not set or less than 1, this will default to the available number of cores.
     * @return the max number of CPU Cores to Use
     */
    int getMaxNumberOfCoresToUse();
}
