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

package org.kinotic.continuum.internal.core.api;

import org.kinotic.continuum.core.api.ClusterService;
import org.kinotic.continuum.internal.core.api.aignite.IgniteServiceAdapter;
import org.kinotic.continuum.internal.utils.IgniteUtil;
import org.apache.commons.lang3.Validate;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteServices;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.internal.IgniteKernal;
import org.apache.ignite.internal.IgnitionEx;
import org.apache.ignite.services.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of the Continuum {@link ClusterService}
 *
 * Created by navid on 10/15/19
 */
@Component
@ConditionalOnProperty(
        value="continuum.disableClustering",
        havingValue = "false",
        matchIfMissing = true)
public class DefaultClusterService implements ClusterService {

    private final Ignite ignite;

    public DefaultClusterService(Ignite ignite) {
        this.ignite = ignite;
    }

    public Mono<Void> deployClusterSingleton(String serviceIdentifier, Class<?> serviceClass, Object ... constructorArgs){
        return _deployClusterSingleton(ignite.services(), serviceIdentifier, serviceClass, constructorArgs);
    }

    public Mono<Void> deployClusterSingletonRemotely(String serviceIdentifier, Class<?> serviceClass, Object ... constructorArgs){
        // Limit service deployment only to remote nodes (exclude the local node).
        ClusterGroup remoteGroup = ignite.cluster().forRemotes();
        IgniteServices igniteServices = ignite.services(remoteGroup);

        return _deployClusterSingleton(igniteServices, serviceIdentifier, serviceClass, constructorArgs);
    }

    public Mono<Void> deployNodeSingleton(String serviceIdentifier, Class<?> serviceClass, Object ... constructorArgs){
        validateClass(serviceClass);
        return IgniteUtil.futureToMono(() -> ignite.services()
                                                   .deployNodeSingletonAsync(serviceIdentifier,
                                                      createIgniteService(serviceIdentifier,
                                                                          serviceClass,
                                                                          constructorArgs)));
    }

    @Override
    public Mono<Boolean> isServiceDeployed(String serviceIdentifier) {
        return Mono.create(sink -> {
            try {
                // first check if local service exists
                if (ignite.services().service(serviceIdentifier) != null) {

                    sink.success(true);

                } else {

                    IgniteKernal kernal = IgnitionEx.gridx(ignite.name());
                    kernal.context().gateway().readLock();

                    // secondary try since we have readlock above we must always unlock
                    try {

                        Map<UUID, Integer> topology = kernal.context()
                                                            .service()
                                                            .serviceTopology(serviceIdentifier, 0);

                        // if any nodes in the topology are returned the service exists and is deployed
                        if (topology != null && !topology.isEmpty()) {
                            sink.success(true);
                        } else {
                            sink.success(false);
                        }

                    }catch (Exception ex){
                        sink.error(ex);
                    } finally {
                        kernal.context().gateway().readUnlock();
                    }
                }
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    @Override
    public Mono<Void> unDeployService(String serviceIdentifier) {
        return IgniteUtil.futureToMono(() -> ignite.services().cancelAsync(serviceIdentifier));
    }

    private Mono<Void> _deployClusterSingleton(IgniteServices igniteServices, String serviceIdentifier, Class<?> serviceClass, Object ... constructorArgs){
        validateClass(serviceClass);
        return IgniteUtil.futureToMono(() -> igniteServices.deployClusterSingletonAsync(serviceIdentifier,
                                                                                        createIgniteService(serviceIdentifier,
                                                                                                             serviceClass,
                                                                                                             constructorArgs)));
    }

    private Service createIgniteService(String serviceIdentifier, Class<?> serviceClass, Object[] constructorArgs){
        return new IgniteServiceAdapter(serviceIdentifier, serviceClass, constructorArgs);
    }

    private void validateClass(Class<?> serviceClass){
        Validate.isTrue(!serviceClass.isInterface(), "Service Class must NOT be an Interface");
        Validate.isTrue(!Modifier.isAbstract(serviceClass.getModifiers()), "Service Class must NOT be Abstract");
    }

}
