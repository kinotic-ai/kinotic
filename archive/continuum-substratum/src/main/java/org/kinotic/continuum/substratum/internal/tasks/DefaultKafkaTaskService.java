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

package org.kinotic.continuum.substratum.internal.tasks;

import org.kinotic.aws.s3.S3Service;
import org.kinotic.continuum.grind.api.JobDefinition;
import org.kinotic.continuum.grind.api.JobScope;
import org.kinotic.continuum.grind.api.Task;
import org.kinotic.continuum.grind.api.Tasks;
import org.kinotic.continuum.substratum.internal.aws.KafkaService;
import org.kinotic.continuum.substratum.internal.util.Names;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.kafka.KafkaAsyncClient;
import software.amazon.awssdk.services.kafka.model.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 *
 * Created by Navid Mitchell on 4/2/20
 */
@Component
public class DefaultKafkaTaskService {

    private final KafkaAsyncClient kafkaAsyncClient;

    private final KafkaService kafkaService;

    private final S3Service s3Service;

    private final DefaultVpcTaskService vpcTaskService;

    public DefaultKafkaTaskService(KafkaAsyncClient kafkaAsyncClient,
                                   KafkaService kafkaService,
                                   S3Service s3Service,
                                   DefaultVpcTaskService vpcTaskService) {
        this.kafkaAsyncClient = kafkaAsyncClient;
        this.kafkaService = kafkaService;
        this.s3Service = s3Service;
        this.vpcTaskService = vpcTaskService;
    }

    /**
     * Create a new Kafka Configuration
     * @param domainName the domain name to use when creating this config
     * @return a Task producing the ARN of the created configuration
     */
    public Task<CompletableFuture<String>> createKafkaConfiguration(String domainName){
        return Tasks.fromSupplier("Create MSK Cluster Config for Domain " + domainName,
                                  () ->kafkaService.createKafkaConfiguration(domainName));
    }


    public Task<CompletableFuture<String>> getKafkaClusterARN(String domainName){
        String clusterName = Names.mskClusterName(domainName);
        return Tasks.fromSupplier("Finding ARN for Kafka Cluster "+clusterName,
                                  () -> {
                   ListClustersRequest request =
                           ListClustersRequest.builder()
                                              .clusterNameFilter(clusterName)
                                              .build();
                   return kafkaAsyncClient.listClusters(request)
                                          .thenApply(response -> {
                                              if (response.hasClusterInfoList()){
                                                  return response.clusterInfoList().get(0).clusterArn();
                                              }else{
                                                  throw new IllegalArgumentException("No MSK Cluster can be found for "+clusterName);
                                              }
                                          });
               });
    }

    public Task<Void> writeKafkaClusterInfoToS3PropertyFile(String domainName,
                                                            String zookeeperConnectionString,
                                                            String kafkaBootstrapBrokers){
        return Tasks.fromRunnable("Writing Kafka Cluster Info to S3 property file", () -> {
            try {
                String bucketName = Names.continuumConfigurationS3BucketName(domainName);
                File tempFile = Files.createTempFile("continuum", null).toFile();
                BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile));
                bw.write("continuum.zookeeperServers="+zookeeperConnectionString+"\n");
                bw.write("continuum.kafkaBootstrapServers="+kafkaBootstrapBrokers+"\n");
                bw.flush();
                bw.close();

                s3Service.createBucketIfDoesNotExist(bucketName);

                s3Service.putFile(bucketName, "application.properties", tempFile);

                tempFile.delete();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public JobDefinition provisionKafkaCluster(String domainName,
                                               String instanceType){
        return JobDefinition.create("Provision Kafka Cluster for Domain "+domainName, JobScope.ISOLATED)
            .jobDefinition(vpcTaskService.ensureSubnetsInContext(domainName, Map.of("scope", "public")))
            .jobDefinition(vpcTaskService.ensureSecurityGroupIdInContext(domainName,
                                                                         // FIXME: use kafka specific security group
                                                                         Names.continuumClusterAwareSecurityGroupName(domainName),
                                                                         "clusterAwareSgId"))
            .taskStoreResult(createKafkaConfiguration(domainName),"kafkaConfigArn")
            .taskStoreResult(Tasks.fromSupplier("Create Kafka Cluster for Domain "+domainName,
                                                new Supplier<CompletableFuture<String>>() {

                @Value("${clusterAwareSgId}")
                private String securityGroupId;

                @Value("${kafkaConfigArn}")
                private String kafkaConfigArn;

                @Autowired
                private List<Subnet> subnets;

                @Override
                public CompletableFuture<String> get() {
                    List<String> subnetIds = new ArrayList<>(subnets.size());
                    for(Subnet subnet: subnets){
                        subnetIds.add(subnet.subnetId());
                    }
                    return kafkaService.createKafkaCluster(domainName,
                                                           instanceType,
                                                           kafkaConfigArn,
                                                           subnetIds,
                                                           Collections.singletonList(securityGroupId))
                                        .thenApply(CreateClusterResponse::clusterArn);
                }
            }), "kafkaClusterARN")
            .jobDefinition(retrieveKafkaConfigurationAndStoreInS3PropertiesFile(domainName));
    }

    public JobDefinition retrieveKafkaConfigurationAndStoreInS3PropertiesFile(String domainName){
        return JobDefinition.create("Retrieve Kafka Cluster Info for Domain "+domainName, JobScope.ISOLATED)
            .taskStoreResult(Tasks.fromSupplier("Build Task Get Kafka ARN",
                                                new Supplier<Task<CompletableFuture<String>>>() {
                @Value("${kafkaClusterARN}")
                private String kafkaClusterARN;

                @Override
                public Task<CompletableFuture<String>> get() {
                    Task<CompletableFuture<String>> ret;
                    if(kafkaClusterARN.equals("${kafkaClusterARN}")){
                        ret = getKafkaClusterARN(domainName);
                    }else{
                        ret = Tasks.noop("kafkaClusterARN already in context");
                    }
                    return ret;
                }
            }), "kafkaClusterARN")
            // This task will block until the kafka cluster is available
            .taskStoreResult(Tasks.fromSupplier("Get Zookeeper Connection String (This will take a while!)",
                                                new Supplier<CompletableFuture<String>>() {

                @Value("${kafkaClusterARN}")
                private String kafkaClusterARN;

                @Override
                public CompletableFuture<String> get() {
                    return kafkaService.getZookeeperConnectionString(kafkaClusterARN);
                }
            }), "zookeeperConnectionString")
            .taskStoreResult(Tasks.fromSupplier("Get Bootstrap Brokers",
                                                new Supplier<CompletableFuture<String>>() {

                @Value("${kafkaClusterARN}")
                private String kafkaClusterARN;

                @Override
                public CompletableFuture<String> get() {
                    return kafkaService.getBootstrapBrokers(kafkaClusterARN)
                                       .thenApply(response -> {
                                           if (response.bootstrapBrokerStringTls() != null && !response.bootstrapBrokerStringTls().isEmpty()){
                                               return response.bootstrapBrokerStringTls();
                                           }else{
                                               return response.bootstrapBrokerString();
                                           }
                                       });
                }
            }), "kafkaBootstrapBrokers")
            .task(Tasks.fromSupplier("Build Task Write Kafka Info to S3",
                                     new Supplier<Task<Void>>() {

                @Value("${zookeeperConnectionString}")
                private String zookeeperConnectionString;

                @Value("${kafkaBootstrapBrokers}")
                private String kafkaBootstrapBrokers;

                @Override
                public Task<Void> get() {
                    return writeKafkaClusterInfoToS3PropertyFile(domainName, zookeeperConnectionString, kafkaBootstrapBrokers);
                }
            }));
    }

    public JobDefinition deleteKafkaCluster(String domainName){
        String clusterName = Names.mskClusterName(domainName);
        return JobDefinition.create()
            .taskStoreResult(Tasks.fromSupplier("Finding ARN for Kafka Cluster "+clusterName,
                                                () -> {
                                                             ListClustersRequest request =
                                                                     ListClustersRequest.builder()
                                                                                        .clusterNameFilter(clusterName)
                                                                                        .build();
                                                             return kafkaAsyncClient.listClusters(request)
                                                                                    .thenApply(ListClustersResponse::clusterInfoList);
                                                         }), "kafkaClusterInfo")

            .task(Tasks.fromSupplier("Delete Kafka Cluster " + clusterName,
                                     new Supplier<CompletableFuture<DeleteClusterResponse>>() {

                                                  @Autowired
                                                  private List<ClusterInfo> clusterInfoList;

                                                  @Override
                                                  public CompletableFuture<DeleteClusterResponse> get() {
                                                      String clusterArn = null;
                                                      for(ClusterInfo info: clusterInfoList){
                                                          if(info.clusterName().equals(clusterName)){
                                                              clusterArn = info.clusterArn();
                                                              break;
                                                          }
                                                      }
                                                      if(clusterArn == null){
                                                          throw new IllegalStateException("Could not find Kafka Cluster "+clusterName);
                                                      }
                                                      DeleteClusterRequest request =
                                                              DeleteClusterRequest.builder()
                                                                                  .clusterArn(clusterArn)
                                                                                  .build();
                                                      return kafkaAsyncClient.deleteCluster(request);
                                                  }
                                              }));
    }


}
