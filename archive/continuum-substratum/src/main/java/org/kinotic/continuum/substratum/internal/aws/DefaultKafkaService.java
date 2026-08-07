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

import org.kinotic.continuum.substratum.internal.util.Names;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kafka.KafkaAsyncClient;
import software.amazon.awssdk.services.kafka.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * A wrapper over {@link software.amazon.awssdk.services.kafka.KafkaAsyncClient} to simplify making requests to AWS
 *
 * Created by Navid Mitchell on 11/12/20
 */
@Component
public class DefaultKafkaService implements KafkaService {

    private static final Logger log = LoggerFactory.getLogger(DefaultKafkaService.class);

    private static final String DEFAULT_KAFKA_VERSION = "2.6.2";

    private final KafkaAsyncClient kafkaAsyncClient;

    public DefaultKafkaService(KafkaAsyncClient kafkaAsyncClient) {
        this.kafkaAsyncClient = kafkaAsyncClient;
    }

    @Override
    public CompletableFuture<String> createKafkaConfiguration(String domainName){
        String configName = Names.mskClusterConfigName(domainName);

        CreateConfigurationRequest request =
                CreateConfigurationRequest.builder()
                                          .name(configName)
                                          .description("Custom Kafka configuration for domain "+domainName)
                                          .serverProperties(getBytesForFile("classpath:kafka/server.properties"))
                                          .build();

        return kafkaAsyncClient.createConfiguration(request)
                               .thenApply(CreateConfigurationResponse::arn);
    }

    private SdkBytes getBytesForFile(String fileName){
        try {
            File file = ResourceUtils.getFile(fileName);
            return SdkBytes.fromByteArray(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public CompletableFuture<CreateClusterResponse> createKafkaCluster(String domainName,
                                                                       String instanceType,
                                                                       String configurationArn,
                                                                       Collection<String> clientSubnets,
                                                                       Collection<String> securityGroups){
        String clusterName = Names.mskClusterName(domainName);
        CreateClusterRequest request =
                CreateClusterRequest.builder()
                                    .clusterName(clusterName)
                                    .kafkaVersion(DEFAULT_KAFKA_VERSION)
                                    .numberOfBrokerNodes(3)
                                    .encryptionInfo(builder -> builder.encryptionInTransit(transBuilder -> transBuilder.inCluster(false)
                                                                                                                       .clientBroker(ClientBroker.PLAINTEXT)))
                                    .configurationInfo(builder -> builder.arn(configurationArn).revision(1L))
                                    .loggingInfo(builder -> builder.brokerLogs(BrokerLogs.builder()
                                                                                         .cloudWatchLogs(CloudWatchLogs.builder()
                                                                                                                       .logGroup("/continuum/") // FIXME: add to VPC provisioning
                                                                                                                       .enabled(true).build())
                                                                                         .build()))
                                    .brokerNodeGroupInfo(builder -> builder.instanceType(instanceType)
                                                                           .clientSubnets(clientSubnets)
                                                                           .securityGroups(securityGroups))
                                    .build();
        return kafkaAsyncClient.createCluster(request);
    }

    @Override
    public CompletableFuture<String> getZookeeperConnectionString(String mskClusterARN){

        // We use the retry policy to continually retry our request until we get the result we want
        // Which makes the conditions a little strange since we are reporting a retryable failure in case of the cluster still starting
        RetryPolicy<DescribeClusterResponse> retryPolicy = new RetryPolicy<DescribeClusterResponse>()

                .abortIf(describeClusterResponse -> describeClusterResponse.clusterInfo().state() != ClusterState.CREATING
                        && describeClusterResponse.clusterInfo().state() != ClusterState.UPDATING
                        && describeClusterResponse.clusterInfo().state() != ClusterState.ACTIVE)

                .handleResultIf(describeClusterResponse -> describeClusterResponse.clusterInfo().state() == ClusterState.CREATING
                                    || describeClusterResponse.clusterInfo().state() == ClusterState.UPDATING)

                .withDelay(Duration.ofMinutes(3))
                .withMaxAttempts(-1);

        return Failsafe.with(retryPolicy)
                       .getStageAsync(() -> {
                           DescribeClusterRequest request =
                                   DescribeClusterRequest
                                           .builder()
                                           .clusterArn(mskClusterARN)
                                           .build();
                           log.debug("Requesting Kafka Cluster Information");
                           return kafkaAsyncClient.describeCluster(request);
                       })
                       .thenApply(
                               describeClusterResponse -> {
                                   if(describeClusterResponse.clusterInfo().state() == ClusterState.DELETING){
                                       throw new IllegalStateException("Kafka Cluster has been deleted");
                                   }else if(describeClusterResponse.clusterInfo().state() == ClusterState.FAILED){
                                       throw new IllegalStateException("Kafka Cluster has failed. "+describeClusterResponse.clusterInfo().stateInfo().message());
                                   }else if(describeClusterResponse.clusterInfo().state() != ClusterState.ACTIVE){
                                       throw new IllegalStateException("Kafka Cluster is not ACTIVE. Cluster State: "+describeClusterResponse.clusterInfo().stateAsString()
                                                                               +" Reason: "+describeClusterResponse.clusterInfo().stateInfo().message());
                                   }
                                   return describeClusterResponse.clusterInfo().zookeeperConnectString();
                               });
    }

    @Override
    public CompletableFuture<GetBootstrapBrokersResponse> getBootstrapBrokers(String mskClusterARN){
        GetBootstrapBrokersRequest request =
                GetBootstrapBrokersRequest.builder()
                                          .clusterArn(mskClusterARN)
                                          .build();

        return kafkaAsyncClient.getBootstrapBrokers(request);
    }


}
