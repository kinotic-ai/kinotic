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

package org.kinotic.rpc.internal.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.ignite.IgniteClusterManager;
import org.apache.ignite.Ignite;
import org.kinotic.rpc.api.config.ContinuumProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.concurrent.TimeUnit.MINUTES;


/**
 * Created by navid on 4/16/15.
 */
@Configuration
public class ContinuumVertxConfig {

    @Bean
    @ConditionalOnProperty(
            value="continuum.disableClustering",
            havingValue = "false",
            matchIfMissing = true)
    public ClusterManager clusterManager(Ignite ignite){
        if(ignite == null){
            throw new IllegalStateException("Something is wrong with the configuration Ignite is null");
        }
        // make sure clustering is enabled
        System.setProperty("vertx.clustered","true");

        return new IgniteClusterManager(ignite);
    }

    @Bean
    public EventBus eventBus(Vertx vertx) {
        return vertx.eventBus();
    }

    @Bean
    public FileSystem fileSystem(Vertx vertx) {
        return vertx.fileSystem();
    }

    @Bean
    public SharedData sharedData(Vertx vertx) {
        return vertx.sharedData();
    }

    @Bean
    public Vertx vertx(ContinuumProperties properties,
                       @Autowired(required = false) ClusterManager clusterManager) throws Throwable {

        VertxBuilder builder = Vertx.builder();

        if (clusterManager != null) {

            EventBusOptions eventBusOptions = new EventBusOptions();
            eventBusOptions.setPort(properties.getEventBusClusterPort());
            eventBusOptions.setHost(properties.getEventBusClusterHost());

            if(properties.getEventBusClusterPublicPort() != -1) {
                eventBusOptions.setClusterPublicPort(properties.getEventBusClusterPublicPort());
            }
            if(properties.getEventBusClusterPublicHost() != null) {
                eventBusOptions.setClusterPublicHost(properties.getEventBusClusterPublicHost());
            }

            VertxOptions options = new VertxOptions()
                    .setEventBusOptions(eventBusOptions);

            return builder.with(options)
                          .withClusterManager(clusterManager)
                          .buildClustered()
                          .toCompletionStage()
                          .toCompletableFuture()
                          .get(2, MINUTES);
        }else{
            return builder.build();
        }
    }

}
