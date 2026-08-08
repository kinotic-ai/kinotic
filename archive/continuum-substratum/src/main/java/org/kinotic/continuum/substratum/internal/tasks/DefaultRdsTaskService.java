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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kinotic.continuum.grind.api.JobDefinition;
import org.kinotic.continuum.grind.api.JobScope;
import org.kinotic.continuum.grind.api.Task;
import org.kinotic.continuum.grind.api.Tasks;
import org.kinotic.continuum.internal.utils.SecurityUtil;
import org.kinotic.continuum.substratum.internal.util.Names;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.rds.RdsAsyncClient;
import software.amazon.awssdk.services.rds.model.*;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.*;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * Created by Navid Mitchell on 7/31/20
 */
// TODO: add logic to delete rds. So far you have to remove rds instances and cluster. Then rds subnet group then secret from secrets manager.
@Component
public class DefaultRdsTaskService {

    private final RdsAsyncClient rdsAsyncClient;

    private final DefaultVpcTaskService vpcTaskService;

    private final SecretsManagerAsyncClient secretsManagerClient;

    private final ObjectMapper objectMapper;

    public DefaultRdsTaskService(RdsAsyncClient rdsAsyncClient,
                                 DefaultVpcTaskService vpcTaskService,
                                 SecretsManagerAsyncClient secretsManagerClient, ObjectMapper objectMapper) {
        this.rdsAsyncClient = rdsAsyncClient;
        this.vpcTaskService = vpcTaskService;
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = objectMapper;
    }

    public JobDefinition provisionIamRds(String domainName, boolean production){
        return JobDefinition.create("Provision IAM RDS", JobScope.ISOLATED)
                            .jobDefinition(vpcTaskService.ensureSubnetsInContext(domainName, Map.of("scope", "private")))
                            .jobDefinition(vpcTaskService.ensureSecurityGroupIdInContext(domainName,
                                                                                         Names.continuumIamDatabaseSecurityGroupName(domainName),
                                                                                         "iamDbSgId"))
                            .taskStoreResult(Tasks.fromCallable("Create IAM RDS Subnet Group",
                                                                new Callable<CompletableFuture<DBSubnetGroup>>() {

                                @Autowired
                                private List<Subnet> subnets;

                                @Override
                                public CompletableFuture<DBSubnetGroup> call() {
                                    List<String> subnetIds = subnets.stream()
                                                                    .map(Subnet::subnetId)
                                                                    .collect(Collectors.toList());

                                    CreateDbSubnetGroupRequest request =
                                            CreateDbSubnetGroupRequest.builder()
                                                                      .dbSubnetGroupName(Names.continuumIamDatabaseSubnetGroupName(domainName))
                                                                      .dbSubnetGroupDescription("IAM RDS Subnet Group")
                                                                      .subnetIds(subnetIds)
                                                                      .build();
                                    return rdsAsyncClient.createDBSubnetGroup(request)
                                                         .thenApply(CreateDbSubnetGroupResponse::dbSubnetGroup);
                                }
                            }))
                            .task(Tasks.fromCallable(new Callable<>() {

                                @Value("${iamDbSgId}")
                                private String iamDbSgId;

                                @Autowired
                                private DBSubnetGroup subnetGroup;

                                @Override
                                public JobDefinition call() {

                                    RdsCreateConfig rdsCreateConfig = new RdsCreateConfig()
                                            .setDomain(domainName)
                                            .setSuffix("iam")
                                            .setDatabaseName("continuum_iam")
                                            .setMasterUsername(SecurityUtil.generateRandomAlphaUser(20))
                                            .setMasterPassword(SecurityUtil.generateRandomPassword(90))
                                            .setDbSubnetGroupName(subnetGroup.dbSubnetGroupName())
                                            .setVpcSecurityGroupIds(List.of(iamDbSgId))
                                            .setDbInstanceClass("db.r5.large")
                                            .setStorageEncrypted(true)
                                            .setAdditionalSecrets(Map.of("secret", SecurityUtil.generateRandomPassword(64)));

                                    if(production){
                                        rdsCreateConfig.setNumberOfReadReplicas(1);
                                        rdsCreateConfig.setDeleteProtection(true);
                                    }

                                    return provisionPostgresCluster(rdsCreateConfig);
                                }
                            }));
    }

    public JobDefinition provisionPostgresCluster(RdsCreateConfig createConfig){

        String dbClusterIdentifier = Names.continuumRdsName(createConfig.getDomain(), createConfig.getSuffix());

        JobDefinition ret = JobDefinition.create()
                                         .taskStoreResult(createPostgresCluster(createConfig))
                                         .task(Tasks.fromCallable("Store RDS connection info in Secrets Manager",
                                                                  new Callable<>() {

                                             @Autowired
                                             private DBCluster dbCluster;

                                             @Override
                                             public CompletableFuture<Void> call() throws Exception {
                                                 String secretName = dbClusterIdentifier+"-meta";

                                                 Map<String, String> dbInfo = new HashMap<>(createConfig.getAdditionalSecrets());
                                                 dbInfo.putAll(Map.of("username", createConfig.getMasterUsername(),
                                                                      "password", createConfig.getMasterPassword(),
                                                                      "endpoint", dbCluster.endpoint(),
                                                                      "jdbcUrl", "jdbc:postgresql://"
                                                                                          + dbCluster.endpoint()
                                                                                          + ":5432/"
                                                                                          + createConfig.getDatabaseName()));


                                                 String json = objectMapper.writeValueAsString(dbInfo);

                                                 // Create or update secret in AWS secrets manager to contain RDS connection information
                                                 return secretsManagerClient.describeSecret(b -> b.secretId(secretName))
                                                                            .thenCompose((Function<DescribeSecretResponse, CompletionStage<Void>>)
                                                                                                 describeSecretResponse -> {

                                                                                UpdateSecretRequest request =  UpdateSecretRequest.builder()
                                                                                                                                  .secretId(dbClusterIdentifier+"-meta")
                                                                                                                                  .secretString(json)
                                                                                                                                  .build();

                                                                                return secretsManagerClient.updateSecret(request).thenApply(v -> null);
                                                                            })
                                                                            .exceptionallyCompose(throwable -> {
                                                                                CompletableFuture<Void> ret;
                                                                                // if the resource was not found this we create the secret for the first time
                                                                                if((throwable instanceof ResourceNotFoundException)
                                                                                   || (throwable.getCause() instanceof ResourceNotFoundException)){

                                                                                        CreateSecretRequest request = CreateSecretRequest.builder()
                                                                                                                                         .name(dbClusterIdentifier + "-meta")
                                                                                                                                         .secretString(json)
                                                                                                                                         .build();

                                                                                        ret = secretsManagerClient.createSecret(request)
                                                                                                                  .thenApply(v -> null);
                                                                                }else{
                                                                                    ret = CompletableFuture.failedFuture(throwable);
                                                                                }
                                                                                return ret;
                                                                            });
                                             }
                                         }))
                                         .task(createPostgresInstance(dbClusterIdentifier,
                                                                      "primary",
                                                                      createConfig.getDbInstanceClass()));

        for(int i = 0; i < createConfig.getNumberOfReadReplicas(); i++){
            ret.task(createPostgresInstance(dbClusterIdentifier, "replica-"+i, createConfig.getDbInstanceClass()));
        }

        return ret;
    }

    private Task<CompletableFuture<DBCluster>> createPostgresCluster(RdsCreateConfig createConfig){
        String dbClusterIdentifier = Names.continuumRdsName(createConfig.getDomain(), createConfig.getSuffix());
        return Tasks.fromSupplier("Creating RDS Postgres Cluster "+dbClusterIdentifier,
                                  () -> {
                   CreateDbClusterRequest request = CreateDbClusterRequest.builder()
                                                                          .dbClusterIdentifier(dbClusterIdentifier)
                                                                          .databaseName(createConfig.getDatabaseName())
                                                                          .masterUsername(createConfig.getMasterUsername())
                                                                          .masterUserPassword(createConfig.getMasterPassword())
                                                                          .vpcSecurityGroupIds(createConfig.getVpcSecurityGroupIds())
                                                                          .dbSubnetGroupName(createConfig.getDbSubnetGroupName())
                                                                          .storageEncrypted(createConfig.isStorageEncrypted())
                                                                          .engine("aurora-postgresql")
                                                                          .backupRetentionPeriod(7)
                                                                          .preferredBackupWindow("05:00-06:00")
                                                                          .preferredMaintenanceWindow("Sun:06:01-Sun:07:00")
                                                                          .deletionProtection(createConfig.isDeleteProtection())
                                                                          .build();

                   return rdsAsyncClient.createDBCluster(request).thenApply(CreateDbClusterResponse::dbCluster);
               });
    }

    public Task<CompletableFuture<CreateDbInstanceResponse>> createPostgresInstance(String dbClusterIdentifier,
                                                                                    String dbInstanceIdentifier,
                                                                                    String dbInstanceClass){
        return Tasks.fromSupplier("Creating RDS Postgres Instance "+dbClusterIdentifier,
                                  () -> {
                   CreateDbInstanceRequest request = CreateDbInstanceRequest.builder()
                                                                            .dbClusterIdentifier(dbClusterIdentifier)
                                                                            .dbInstanceIdentifier(dbInstanceIdentifier)
                                                                            .dbInstanceClass(dbInstanceClass)
                                                                            .engine("aurora-postgresql")
                                                                            .build();

                   return rdsAsyncClient.createDBInstance(request);
               });
    }


}
