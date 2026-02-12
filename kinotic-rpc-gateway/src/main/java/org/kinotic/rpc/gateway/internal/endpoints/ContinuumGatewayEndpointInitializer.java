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

package org.kinotic.rpc.gateway.internal.endpoints;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kinotic.rpc.api.config.ContinuumProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


/**
 *
 * Created by navid on 2/10/20
 */
@Component
@RequiredArgsConstructor
public class ContinuumGatewayEndpointInitializer {
    private static final Logger log = LoggerFactory.getLogger(ContinuumGatewayEndpointInitializer.class);

    private final ContinuumVertcleFactory continuumVertcleFactory;
    private final ContinuumProperties continuumProperties;
    private final Vertx vertx;

    @PostConstruct
    public void init(){
        // If production deploy one verticle of each per core
        int numToDeploy = continuumProperties.getMaxNumberOfCoresToUse();
        log.info("{} Cores will be used for Continuum Endpoints", numToDeploy);
        DeploymentOptions options = new DeploymentOptions().setInstances(numToDeploy);

        log.info("Deploying {} Stomp Server Endpoint(s)", numToDeploy);
        vertx.deployVerticle(continuumVertcleFactory::createStompServerVerticle, options);

        log.info("Deploying {} REST Server Endpoint(s)", numToDeploy);
        vertx.deployVerticle(continuumVertcleFactory::createRestServerVerticle, options);
    }

}
