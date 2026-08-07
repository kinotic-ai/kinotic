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

package org.kinotic.continuum.substratum.internal.aws;

import software.amazon.awssdk.services.kafka.model.CreateClusterResponse;
import software.amazon.awssdk.services.kafka.model.GetBootstrapBrokersResponse;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * A wrapper over {@link software.amazon.awssdk.services.kafka.KafkaAsyncClient} to simplify making requests to AWS
 *
 * Created by Navid Mitchell on 11/12/20
 */
public interface KafkaService {

    /**
     * Create a new Kafka Configuration
     * @param domainName the domain name to use when creating this config
     * @return a Task producing the ARN of the created configuration
     */
    CompletableFuture<String> createKafkaConfiguration(String domainName);

    CompletableFuture<CreateClusterResponse> createKafkaCluster(String domainName,
                                                                String instanceType,
                                                                String configurationArn,
                                                                Collection<String> clientSubnets,
                                                                Collection<String> securityGroups);

    CompletableFuture<String> getZookeeperConnectionString(String mskClusterARN);

    CompletableFuture<GetBootstrapBrokersResponse> getBootstrapBrokers(String mskClusterARN);


}
