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

import org.kinotic.continuum.grind.api.JobDefinition;
import org.kinotic.continuum.grind.api.JobScope;
import org.kinotic.continuum.grind.api.Task;
import org.kinotic.continuum.grind.api.Tasks;
import org.kinotic.continuum.substratum.internal.config.ContinuumSubstratumProperties;
import org.kinotic.continuum.substratum.internal.util.AwsRequestUtils;
import org.kinotic.continuum.substratum.internal.util.Names;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ecs.EcsAsyncClient;
import software.amazon.awssdk.services.ecs.model.Scope;
import software.amazon.awssdk.services.ecs.model.Volume;
import software.amazon.awssdk.services.ecs.model.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 *
 * Created by Navid Mitchell on 4/2/20
 */
@Component
public class DefaultECSTaskService {

    private final ContinuumSubstratumProperties properties;
    private final EcsAsyncClient ecsAsyncClient;
    private final Ec2AsyncClient ec2AsyncClient;
    private final DefaultVpcTaskService vpcTaskService;

    private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss ZZZ");

    public DefaultECSTaskService(ContinuumSubstratumProperties properties,
                                 EcsAsyncClient ecsAsyncClient,
                                 Ec2AsyncClient ec2AsyncClient,
                                 DefaultVpcTaskService vpcTaskService) {
        this.properties = properties;
        this.ecsAsyncClient = ecsAsyncClient;
        this.ec2AsyncClient = ec2AsyncClient;
        this.vpcTaskService = vpcTaskService;
    }

    public Task<CompletableFuture<Cluster>> createCluster(String domainName){
        String clusterName = Names.ecsClusterName(domainName);
        return Tasks.fromSupplier("Create ECS Cluster " + clusterName,
                                  () -> {
                   CreateClusterRequest request =
                           CreateClusterRequest.builder()
                                               .clusterName(clusterName)
                                               .build();
                   return ecsAsyncClient.createCluster(request).thenApply(CreateClusterResponse::cluster);
               });
    }

    /**
     * Launches an EC2 Instance for use within a given ECS cluster
     * @param domainName that defines which ECS cluster to use
     * @param instanceType the AWS EC2 type of the instance to launch
     * @param continuumType adds a continuum.type ECS attribute to the instance for use when placing tasks
     *                      additionally this will be used in the instance name.
     * @param subnetScope run in subnets with a scope tag matching this value.
     *                    During provisioning of continuum ecs cluster we define a public and private scope.
     * @param numberToLaunch the number of instances to launch
     * @return
     */
    public JobDefinition runContinuumInstances(String domainName,
                                               String instanceType,
                                               String continuumType,
                                               String subnetScope,
                                               int numberToLaunch){
        return JobDefinition.create("Run ECS "+continuumType+" Instances", JobScope.ISOLATED)
                            .jobDefinition(vpcTaskService.ensureSubnetsInContext(domainName, Map.of("scope", subnetScope)))
                            .jobDefinition(vpcTaskService.ensureSecurityGroupIdInContext(domainName,
                                                                                         Names.supportAccessSecurityGroupName(domainName),
                                                                                         "supportAccessSgId"))
                            .task(Tasks.fromCallable("Build Run "+continuumType+" Instances JobDefinition",
                                                                  new Callable<>() {

                                @Value("${supportAccessSgId}")
                                private String supportAccessSgId;

                                @Autowired
                                private List<Subnet> subnets;

                                @Override
                                public JobDefinition call() {
                                    JobDefinition ret = JobDefinition.create("Run "+numberToLaunch+" Instances");
                                    int subnetIdx = 0;
                                    int subnetLen = subnets.size();
                                    for(int i = 0; i < numberToLaunch; i++){

                                        ret.task(runEcsInstance(domainName,
                                                                instanceType,
                                                                subnets.get(subnetIdx).subnetId(),
                                                                List.of(supportAccessSgId),
                                                                Map.of("Name", Names.ecsContainerInstanceName(domainName, continuumType),
                                                                       "domain", domainName,
                                                                       "createdBy", "substratum",
                                                                       "createdOn", sdf.format(new Date())),
                                                                Map.of("continuum.type", continuumType)));
                                        subnetIdx++;
                                        if(subnetIdx > subnetLen){
                                            subnetIdx = 0;
                                        }
                                    }

                                    return ret;
                                }
                            }));
    }


    /**
     * Starts a new ECS container instance
     * @param domainName the domain that should be used to resolve the correct ECS cluster to use
     * @param instanceType the EC2 instance type to use
     * @param subnetId the EC2 subnetId to use
     * @param securityGroupIds the subnet group id's to use
     * @param tags are added to the ec2 container instance
     * @param attributes can be used for task placement constraints
     * @return the response
     */
    public Task<CompletableFuture<RunInstancesResponse>> runEcsInstance(String domainName,
                                                                        String instanceType,
                                                                        String subnetId,
                                                                        List<String> securityGroupIds,
                                                                        Map<String, String> tags,
                                                                        Map<String, String> attributes){
        String clusterName = Names.ecsClusterName(domainName);

        return Tasks.fromSupplier("Run ECS Instance for Cluster " + clusterName,
                                  () -> {

                   StringBuilder attributeSb = new StringBuilder();
                   if(attributes.size() > 0){
                       attributeSb.append("ECS_INSTANCE_ATTRIBUTES={");
                       int i = 0;
                       for(Map.Entry<String, String> entry : attributes.entrySet()){
                           if(i > 0){
                               attributeSb.append(", ");
                           }
                           attributeSb.append("\\\"");
                           attributeSb.append(entry.getKey());
                           attributeSb.append("\\\": \\\"");
                           attributeSb.append(entry.getValue());
                           attributeSb.append("\\\"");
                           i++;
                       }
                       attributeSb.append("}");
                   }

                   // Configure user data to install proper docker plugins needed for storage volumes
                   String userData = "#!/bin/bash\n" +
                           "echo ECS_CLUSTER=" + clusterName + " >> /etc/ecs/ecs.config\n" +
                           "echo ECS_RESERVED_MEMORY=256 >> /etc/ecs/ecs.config\n" +
                           (attributeSb.length() > 0 ? "echo "+attributeSb.toString()+" >> /etc/ecs/ecs.config\n" : "")+
                           "amazon-linux-extras install epel\n" +
                           "yum install s3fs-fuse\n" +
                           "docker plugin install rexray/ebs REXRAY_PREEMPT=true " +
                                                            "EBS_REGION="+properties.getAwsRegion() +
                                                            " --grant-all-permissions\n" +
                           "docker plugin install rexray/s3fs S3FS_REGION="+properties.getAwsRegion() +
                                                            " LIBSTORAGE_INTEGRATION_VOLUME_OPERATIONS_MOUNT_ROOTPATH=/" +
                                                            " LINUX_VOLUME_ROOTPATH=/" +
                                                            " S3FS_OPTIONS=\"allow_other,iam_role=auto,umask=000\"" +
                                                            " --grant-all-permissions\n" +
                           "sudo systemctl restart docker\n";


                   List<Tag> tagList = new ArrayList<>(tags.size());
                   for(Map.Entry<String, String> entry : tags.entrySet()){
                       tagList.add(Tag.builder().key(entry.getKey()).value(entry.getValue()).build());
                   }
                   TagSpecification tagSpecification = TagSpecification.builder()
                                                                       .resourceType("instance")
                                                                       .tags(tagList)
                                                                       .build();

                   InstanceNetworkInterfaceSpecification.Builder networkInterfaceSpecificationBuilder = InstanceNetworkInterfaceSpecification.builder()
                                                                                                                              .associatePublicIpAddress(true)
                                                                                                                              .deviceIndex(0)
                                                                                                                              .subnetId(subnetId);
                   if(securityGroupIds != null && securityGroupIds.size() > 0){
                       networkInterfaceSpecificationBuilder.groups(securityGroupIds);
                   }

                   IamInstanceProfileSpecification iamInstanceProfileSpecification = IamInstanceProfileSpecification.builder()
                                                                                                                    .name("ecsInstanceRole")
                                                                                                                    .build();

                   RunInstancesRequest request =
                           RunInstancesRequest.builder()
                                              .instanceType(instanceType)
                                              .keyName(Names.keyPairName(domainName))
                                              .userData(Base64.getEncoder().encodeToString(userData.getBytes()))
                                              .tagSpecifications(tagSpecification)
                                              .networkInterfaces(networkInterfaceSpecificationBuilder.build())
                                              .iamInstanceProfile(iamInstanceProfileSpecification)
                                              .minCount(1)
                                              .maxCount(1)
                                              .imageId(getECSOptimizedAMIForCurrentRegion())
                                              .ebsOptimized(true)
                                              .build();

                   return ec2AsyncClient.runInstances(request);
               });

    }

    public Task<CompletableFuture<TaskDefinition>> registerContinuumGatewayServerTaskDefinition(String domainName,
                                                                                                String gitBranch,
                                                                                                float numCpus,
                                                                                                int heapMemoryMB,
                                                                                                int totalMemoryMB){
        return Tasks.fromSupplier("Register Continuum Gateway Task Definition",
                                  () ->{
                   final String serviceName = "continuum-gateway-server";

                   PortMapping stompPortMapping = PortMapping.builder().containerPort(58503).protocol("tcp").build();
                   PortMapping restPortMapping = PortMapping.builder().containerPort(58504).protocol("tcp").build();

                   MountPoint continuumConfigurationMountPoint = AwsRequestUtils.getContinuumDomainConfigurationMountPoint(domainName);

                   Volume continuumConfigurationVolume = getContinuumDomainConfigurationVolume(domainName);


                   MountPoint dataMountPoint = MountPoint.builder()
                                                         .containerPath("/media/continuum/data")
                                                         .sourceVolume(Names.continuumDataVolumeEBSName(domainName, serviceName))
                                                         .build();

                   DockerVolumeConfiguration dataVolumeConfiguration = DockerVolumeConfiguration.builder()
                                                                                                .autoprovision(true)
                                                                                                .scope(Scope.SHARED)
                                                                                                .driver("rexray/ebs")
                                                                                                .driverOpts(Map.of("volumetype",
                                                                                                                   "st1", // throughput optimized
                                                                                                                   "size",
                                                                                                                   "500"))
                                                                                                .build();
                   Volume continuumDataVolume = Volume.builder()
                                                      .name(Names.continuumDataVolumeEBSName(domainName, serviceName))
                                                      .dockerVolumeConfiguration(dataVolumeConfiguration)
                                                      .build();
                   int headRoom = 256; // 256 MB head room for jvm
                   long offHeapMemBytes = (totalMemoryMB - heapMemoryMB - headRoom) * 1024L * 1024L;

                   ContainerDefinition containerDefinition = ContainerDefinition.builder()
                                                                                .name(Names.continuumTaskName(gitBranch, serviceName))
                                                                                .image(Names.continuumEcrImageName(properties.getAwsAccountId(),
                                                                                                                   properties.getAwsRegion(),
                                                                                                                   Names.continuumEcrRepositoryName(gitBranch, serviceName)))
                                                                                .cpu((int)(1024 * numCpus))
                                                                                .memoryReservation(totalMemoryMB)
                                                                                .portMappings(stompPortMapping,
                                                                                              restPortMapping)
                                                                                .essential(true)
                                                                                .logConfiguration(AwsRequestUtils.buildLogConfiguration(gitBranch + "/" + serviceName, properties.getAwsRegion()))
                                                                                .mountPoints(continuumConfigurationMountPoint, dataMountPoint)
                                                                                .environment(KeyValuePair.builder().name("JAVA_OPTS").value("\"-Xms"+heapMemoryMB+"m\" \"-Xmx"+heapMemoryMB+"m\"").build())
                                                                                .command("--spring.config.additional-location=file:/media/continuum/configuration/",
                                                                                         "--spring.profiles.active=production",
                                                                                         "--continuum.maxOffHeapMemory="+offHeapMemBytes)
                                                                                .build();

                   RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
                                                                                        .family(Names.continuumTaskName(gitBranch, serviceName))
                                                                                        .containerDefinitions(containerDefinition)
                                                                                        .networkMode(NetworkMode.AWSVPC)
                                                                                        .requiresCompatibilities(Compatibility.EC2)
                                                                                        .taskRoleArn("arn:aws:iam::"+properties.getAwsAccountId()+":role/ecsTaskRole")
                                                                                        .volumes(continuumConfigurationVolume, continuumDataVolume)
                                                                                        .build();

                   return ecsAsyncClient.registerTaskDefinition(request)
                                        .thenApply(RegisterTaskDefinitionResponse::taskDefinition);
               });
    }

    public Task<CompletableFuture<CreateServiceResponse>> createContinuumGatewayServerService(String domainName,
                                                                                              String gitBranch,
                                                                                              Integer requiredInstances,
                                                                                              List<String> subnetIds,
                                                                                              List<String> securityGroupIds){
        return Tasks.fromSupplier("Create Continuum Gateway Service",
                                  () ->{
                   final String serviceName = "continuum-gateway-server";

                   CreateServiceRequest request = CreateServiceRequest.builder()
                                                                      .serviceName(Names.continuumTaskName(gitBranch, serviceName))
                                                                      .cluster(Names.ecsClusterName(domainName))
                                                                      .taskDefinition(Names.continuumTaskName(gitBranch, serviceName))
                                                                      .desiredCount(requiredInstances)
                                                                      .networkConfiguration(networkConfiguration -> networkConfiguration.awsvpcConfiguration(awsConfig -> {
                                                                          awsConfig.securityGroups(securityGroupIds);
                                                                          awsConfig.subnets(subnetIds);
                                                                      }))
                                                                      .placementConstraints(PlacementConstraint.builder()
                                                                                                               .type(PlacementConstraintType.MEMBER_OF)
                                                                                                               .expression("attribute:continuum.type == frontend")
                                                                                                               .build())
                                                                      .deploymentController(builder -> builder.type(DeploymentControllerType.CODE_DEPLOY))
                                                                      .schedulingStrategy(SchedulingStrategy.REPLICA)
                                                                      .launchType(LaunchType.EC2)
                                                                      .build();

                   return ecsAsyncClient.createService(request);
               });
    }

    public Task<CompletableFuture<TaskDefinition>> registerContinuumIamServerTaskDefinition(String domainName,
                                                                                            String gitBranch,
                                                                                            float numCpus,
                                                                                            int heapMemoryMB,
                                                                                            int totalMemoryMB){
        return Tasks.fromSupplier("Register Continuum Iam Task Definition",
                                  () ->{
                   final String serviceName = "continuum-iam";

                   MountPoint continuumConfigurationMountPoint = AwsRequestUtils.getContinuumDomainConfigurationMountPoint(domainName);

                   Volume continuumConfigurationVolume = getContinuumDomainConfigurationVolume(domainName);

                   int headRoom = 256; // 256 MB head room for jvm
                   long offHeapMemBytes = (totalMemoryMB - heapMemoryMB - headRoom) * 1024L * 1024L;

                   ContainerDefinition containerDefinition = ContainerDefinition.builder()
                                                                                .name(Names.continuumTaskName(gitBranch, serviceName))
                                                                                .image(Names.continuumEcrImageName(properties.getAwsAccountId(),
                                                                                                                   properties.getAwsRegion(),
                                                                                                                   Names.continuumEcrRepositoryName(gitBranch, serviceName)))
                                                                                .cpu((int)(1024 * numCpus))
                                                                                .memoryReservation(totalMemoryMB)
                                                                                .essential(true)
                                                                                .logConfiguration(AwsRequestUtils.buildLogConfiguration(gitBranch + "/" + serviceName, properties.getAwsRegion()))
                                                                                .mountPoints(continuumConfigurationMountPoint)
                                                                                .environment(KeyValuePair.builder().name("JAVA_OPTS").value("\"-Xms"+heapMemoryMB+"m\" \"-Xmx"+heapMemoryMB+"m\"").build())
                                                                                .command("--spring.config.additional-location=file:/media/continuum/configuration/",
                                                                                         "--spring.profiles.active=production",
                                                                                         "--continuum.maxOffHeapMemory="+offHeapMemBytes)
                                                                                .build();

                   RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
                                                                                        .family(Names.continuumTaskName(gitBranch, serviceName))
                                                                                        .containerDefinitions(containerDefinition)
                                                                                        .networkMode(NetworkMode.AWSVPC)
                                                                                        .requiresCompatibilities(Compatibility.EC2)
                                                                                        .taskRoleArn("arn:aws:iam::"+properties.getAwsAccountId()+":role/ecsTaskRole")
                                                                                        .volumes(continuumConfigurationVolume)
                                                                                        .build();

                   return ecsAsyncClient.registerTaskDefinition(request)
                                        .thenApply(RegisterTaskDefinitionResponse::taskDefinition);
               });
    }

    public Task<CompletableFuture<TaskDefinition>> registerContinuumOrbiterServerTaskDefinition(String domainName,
                                                                                                String gitBranch,
                                                                                                float numCpus,
                                                                                                int heapMemoryMB,
                                                                                                int totalMemoryMB){
        return Tasks.fromSupplier("Register Continuum Orbiter Task Definition",
                                  () ->{
                                               final String serviceName = "continuum-orbiter";

                                               MountPoint continuumConfigurationMountPoint = AwsRequestUtils.getContinuumDomainConfigurationMountPoint(domainName);

                                               Volume continuumConfigurationVolume = getContinuumDomainConfigurationVolume(domainName);

                                               int headRoom = 256; // 256 MB head room for jvm
                                               long offHeapMemBytes = (totalMemoryMB - heapMemoryMB - headRoom) * 1024L * 1024L;

                                               ContainerDefinition containerDefinition = ContainerDefinition.builder()
                                                                                                            .name(Names.continuumTaskName(gitBranch, serviceName))
                                                                                                            .image(Names.continuumEcrImageName(properties.getAwsAccountId(),
                                                                                                                                               properties.getAwsRegion(),
                                                                                                                                               Names.continuumEcrRepositoryName(gitBranch, serviceName)))
                                                                                                            .cpu((int)(1024 * numCpus))
                                                                                                            .memoryReservation(totalMemoryMB)
                                                                                                            .essential(true)
                                                                                                            .logConfiguration(
                                                                                                                    AwsRequestUtils
                                                                                                                            .buildLogConfiguration(gitBranch + "/" + serviceName, properties.getAwsRegion()))
                                                                                                            .mountPoints(continuumConfigurationMountPoint)
                                                                                                            .environment(KeyValuePair.builder().name("JAVA_OPTS").value("\"-Xms"+heapMemoryMB+"m\" \"-Xmx"+heapMemoryMB+"m\"").build())
                                                                                                            .command("--spring.config.additional-location=file:/media/continuum/configuration/",
                                                                                                                     "--spring.profiles.active=production",
                                                                                                                     "--continuum.maxOffHeapMemory="+offHeapMemBytes)
                                                                                                            .build();

                                               RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
                                                                                                                    .family(Names.continuumTaskName(gitBranch, serviceName))
                                                                                                                    .containerDefinitions(containerDefinition)
                                                                                                                    .networkMode(NetworkMode.AWSVPC)
                                                                                                                    .requiresCompatibilities(Compatibility.EC2)
                                                                                                                    .taskRoleArn("arn:aws:iam::"+properties.getAwsAccountId()+":role/ecsTaskRole")
                                                                                                                    .volumes(continuumConfigurationVolume)
                                                                                                                    .build();

                                               return ecsAsyncClient.registerTaskDefinition(request)
                                                                    .thenApply(RegisterTaskDefinitionResponse::taskDefinition);
                                           });
    }


    public Task<CompletableFuture<TaskDefinition>> registerContinuumBuilderTaskDefinition(){
        return Tasks.fromSupplier("Register Continuum Builder Task Definition",
                                  () ->{
                   final String serviceName = "continuum-builder";

                   ContainerDefinition dindContainerDefinition = ContainerDefinition.builder()
                                                                                    .name("dind")
                                                                                    .image("docker:dind")
                                                                                    .memoryReservation(512)
                                                                                    .essential(true)
                                                                                    .privileged(true)
                                                                                    .environment(KeyValuePair.builder()
                                                                                                             .name("DOCKER_TLS_CERTDIR")
                                                                                                             .value("")
                                                                                                             .build())
                                                                                    .logConfiguration(AwsRequestUtils.buildLogConfiguration("dind-" + serviceName, properties.getAwsRegion()))
                                                                                    .build();

                   ContainerDependency dindDependency = ContainerDependency.builder()
                                                                           .condition(ContainerCondition.START)
                                                                           .containerName("dind")
                                                                           .build();

                   MountPoint deployKeysMountPoint = MountPoint.builder()
                                                               .containerPath("/media/continuum/deploy-keys")
                                                               .sourceVolume("continuum-fs-deploy-keys")
                                                               .build();

                   MountPoint workMountPoint = MountPoint.builder()
                                                         .containerPath("/media/continuum/work")
                                                         .sourceVolume("continuum-fs-builder-work")
                                                         .build();

                   ContainerDefinition builderContainerDefinition = ContainerDefinition.builder()
                                                                                       .name(serviceName)
                                                                                       .image("kinotic-foundation/continuum-builder")
                                                                                       .memoryReservation(512)
                                                                                       .essential(true)
                                                                                       .links("dind:docker")
                                                                                       .dependsOn(dindDependency)
                                                                                       .logConfiguration(AwsRequestUtils
                                                                                                                 .buildLogConfiguration(serviceName, properties.getAwsRegion()))
                                                                                       .mountPoints(deployKeysMountPoint, workMountPoint)
                                                                                       .build();


                   RegisterTaskDefinitionRequest request = RegisterTaskDefinitionRequest.builder()
                                                                                        .family(serviceName)
                                                                                        .containerDefinitions(dindContainerDefinition, builderContainerDefinition)
                                                                                        .taskRoleArn("arn:aws:iam::" + properties.getAwsAccountId()
                                                                                                                     + ":role/ecsTaskRoleECRFullAccess")
                                                                                        .networkMode(NetworkMode.BRIDGE)
                                                                                        .requiresCompatibilities(Compatibility.EC2)
                                                                                        .volumes(AwsRequestUtils.getContinuumDeployKeysVolume(),
                                                                                                 AwsRequestUtils.getContinuumBuilderWorkVolume())
                                                                                        .build();

                   return ecsAsyncClient.registerTaskDefinition(request)
                                        .thenApply(RegisterTaskDefinitionResponse::taskDefinition);
               });
    }

    public Task<CompletableFuture<RunTaskResponse>> runContinuumBuilder(String ecsBuildClusterName,
                                                                        String gitRepo,
                                                                        String gitBranch,
                                                                        String folder,
                                                                        String ecrRepository){

        return Tasks.fromSupplier("Run Continuum Builder Task",
                                  () ->{

                   String clusterName = ecsBuildClusterName.replace(".", "-").trim();

                   ContainerOverride containerOverride = ContainerOverride.builder()
                                                                          .name("continuum-builder")
                                                                          .command("--gitRepo", gitRepo,
                                                                                   "--gitBranch", gitBranch,
                                                                                   "--folder", folder,
                                                                                   "--awsRegion", properties.getAwsRegion(),
                                                                                   "--ecrRepository", ecrRepository,
                                                                                   "--sshKeysFolder", "/media/continuum/deploy-keys")
                                                                          .build();

                   RunTaskRequest request = RunTaskRequest.builder()
                                                          .cluster(clusterName)
                                                          .count(1)
                                                          .launchType(LaunchType.EC2)
                                                          .taskDefinition("continuum-builder")
                                                          .overrides(b -> b.containerOverrides(containerOverride))
                                                          .build();

                   return ecsAsyncClient.runTask(request);
               });

    }

    private static Volume getContinuumDomainConfigurationVolume(String domainName){
        DockerVolumeConfiguration domainConfigVolumeConfiguration = DockerVolumeConfiguration.builder()
                                                                                             .autoprovision(true)
                                                                                             .scope(Scope.SHARED)
                                                                                             .driver("rexray/s3fs")
                                                                                             .build();
        return Volume.builder()
                     .name(Names.continuumConfigurationS3BucketName(domainName))
                     .dockerVolumeConfiguration(domainConfigVolumeConfiguration)
                     .build();
    }

    /**
     * @return the correct ami id for an ECS optimized Amazon Linux 2 image based on the configured region
     */
    private String getECSOptimizedAMIForCurrentRegion(){
        // TODO: add more id's before open sourcing
        String regionId = properties.getAwsRegion();
        String amiId = null;
        switch(regionId){
            case "us-east-1":
                amiId = "ami-0dc161e2e5f144ffc";
                break;
            case "us-east-2":
                amiId = "ami-09f644e1caad2d877";
                break;
            case "us-west-1":
                amiId = "ami-00db7974d178c2536";
                break;
            case "us-west-2":
                amiId = "ami-0d927e3ac55a7b26f";
                break;
        }
        return amiId;
    }

}
