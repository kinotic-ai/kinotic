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

package org.kinotic.continuum.core.api;

import reactor.core.publisher.Mono;

/**
 * Provides cluster wide service deployment management.
 * All services deployed must have all classes available on any node in the cluster.
 * In general this class is intended for internal use only..
 *
 * Created by navid on 10/15/19
 */
public interface ClusterService {

    /**
     * Asynchronously deploys a cluster-wide singleton service. There will always be
     * one instance of the service in the cluster. In case if grid node on which the service
     * was deployed crashes or stops, the service will be automatically redeployed on another node.
     * However, if the node on which the service is deployed remains in topology, then the
     * service will always be deployed on that node only, regardless of topology changes.
     * <p>
     * Note that in case of topology changes, due to network delays, there may be a temporary situation
     * when a singleton service instance will be active on more than one node (e.g. crash detection delay).
     * <p>
     *
     * @param serviceIdentifier the identifier for the service
     * @param serviceClass service class that will be instantiated upon service start
     * @param constructorArgs the arguments to be used when creating a new instance of the serviceClass
     * @return a {@link Mono<Void>} representing pending completion of the operation
     */
    Mono<Void> deployClusterSingleton(String serviceIdentifier, Class<?> serviceClass, Object... constructorArgs);

    /**
     * Asynchronously deploys a cluster-wide singleton service. Will not deploy the service to the local node. Only remote nodes will be used.
     * There will always be one instance of the service in the cluster. In case if grid node on which the service
     * was deployed crashes or stops, the service will be automatically redeployed on another node.
     * However, if the node on which the service is deployed remains in topology, then the
     * service will always be deployed on that node only, regardless of topology changes.
     * <p>
     * Note that in case of topology changes, due to network delays, there may be a temporary situation
     * when a singleton service instance will be active on more than one node (e.g. crash detection delay).
     * <p>
     *
     * @param serviceIdentifier the identifier for the service
     * @param serviceClass service class that will be instantiated upon service start
     * @param constructorArgs the arguments to be used when creating a new instance of the serviceClass
     * @return a {@link Mono<Void>} representing pending completion of the operation
     */
    Mono<Void> deployClusterSingletonRemotely(String serviceIdentifier, Class<?> serviceClass, Object... constructorArgs);

    /**
     * Asynchronously deploys a per-node singleton service. There will always be
     * one instance of the service running on each node. Whenever new nodes are started
     * within the underlying cluster, a new service instance will automatically be deployed on every new node.
     *
     * @param serviceIdentifier the identifier for the service
     * @param serviceClass service class that will be instantiated upon service start
     * @param constructorArgs the arguments to be used when creating a new instance of the serviceClass
     * @return a {@link Mono<Void>}  representing pending completion of the operation
     */
    Mono<Void> deployNodeSingleton(String serviceIdentifier, Class<?> serviceClass, Object... constructorArgs);

    /**
     * Remove the deployed service from the underlying cluster
     * @param serviceIdentifier the service to be removed
     * @return a {@link Mono<Void>}  representing pending completion of the operation
     */
    Mono<Void> unDeployService(String serviceIdentifier);

    /**
     * Checks if a service with the given identifier is currently deployed to the cluster
     * @param serviceIdentifier the identifier for the service
     * @return a {@link Mono<Boolean>} that will be true if the service is deployed false if not
     */
    Mono<Boolean> isServiceDeployed(String serviceIdentifier);
}
