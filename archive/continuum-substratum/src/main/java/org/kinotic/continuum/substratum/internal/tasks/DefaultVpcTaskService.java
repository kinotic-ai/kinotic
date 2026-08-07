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

import org.kinotic.continuum.grind.api.JobScope;
import org.kinotic.continuum.substratum.internal.util.Names;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.acm.AcmAsyncClient;
import software.amazon.awssdk.services.acm.model.CertificateSummary;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2AsyncClient;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 *
 * Created by Navid Mitchell on 3/24/20
 */
@Component
public class DefaultVpcTaskService {

    private static final Logger log = LoggerFactory.getLogger(DefaultVpcTaskService.class);

    private final Ec2AsyncClient ec2AsyncClient;
    private final DefaultEC2TaskService ec2TaskService;
    private final ElasticLoadBalancingV2AsyncClient elbAsyncClient;
    private final AcmAsyncClient acmAsyncClient;

    private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss ZZZ");

    public DefaultVpcTaskService(Ec2AsyncClient ec2AsyncClient,
                                 DefaultEC2TaskService ec2TaskService,
                                 ElasticLoadBalancingV2AsyncClient elbAsyncClient,
                                 AcmAsyncClient acmAsyncClient) {
        this.ec2AsyncClient = ec2AsyncClient;
        this.ec2TaskService = ec2TaskService;
        this.elbAsyncClient = elbAsyncClient;
        this.acmAsyncClient = acmAsyncClient;
    }

    public Task<CompletableFuture<Vpc>> createVpcTask(){
        return Tasks.fromSupplier("Creating VPC", () -> {
            CreateVpcRequest request =
                    CreateVpcRequest.builder()
                                    .instanceTenancy(Tenancy.DEFAULT)
                                    .cidrBlock("10.0.0.0/16")
                                    .build();

            return ec2AsyncClient.createVpc(request)
                                 .thenApply(CreateVpcResponse::vpc);

        });
    }

    public Task<CompletableFuture<Vpc>> getVpc(String domainName){
        String vpcName = Names.vpcName(domainName);
        return Tasks.fromSupplier("Find VPC Id for name " + vpcName,
                                  () -> {
                                               Filter nameFilter = Filter.builder()
                                                                         .name("tag:Name")
                                                                         .values(vpcName)
                                                                         .build();
                                               DescribeVpcsRequest request =
                                                       DescribeVpcsRequest.builder()
                                                                          .filters(nameFilter)
                                                                          .build();
                                               return ec2AsyncClient.describeVpcs(request)
                                                                    .thenApply(describeVpcsResponse -> {
                                                                        if(!describeVpcsResponse.hasVpcs()){
                                                                            throw new IllegalArgumentException("No VPC can be found with the name "+vpcName);
                                                                        }else if(describeVpcsResponse.vpcs().size() > 1){
                                                                            throw new IllegalStateException("More than one VPC with the name "+vpcName+" was found");
                                                                        }
                                                                        return describeVpcsResponse.vpcs().get(0);
                                                                    });
                                           });
    }

    // TODO: delete dependencies for VPC
    public Task<CompletableFuture<DeleteVpcResponse>> deleteVpcById(String vpcId){
        return Tasks.fromSupplier("Delete VPC id: "+vpcId,
                                  () -> {
                                               DeleteVpcRequest request =
                                                       DeleteVpcRequest.builder()
                                                                       .vpcId(vpcId)
                                                                       .build();
                                               return ec2AsyncClient.deleteVpc(request);
                                           });
    }

    public JobDefinition deleteVpcByName(String vpcName){
        return JobDefinition.create("Delete VPC "+vpcName, JobScope.ISOLATED)
                            .taskStoreResult(getVpc(vpcName))
                            .task(Tasks.fromSupplier(new Supplier<Task<CompletableFuture<DeleteVpcResponse>>>() {
                                @Autowired
                                private Vpc vpc;

                                @Override
                                public Task<CompletableFuture<DeleteVpcResponse>> get() {
                                    return deleteVpcById(vpc.vpcId());
                                }
                            }));
    }

    public JobDefinition ensureVpcInContext(String domainName){
        return JobDefinition.create("Ensure VPC In Scope", JobScope.PARENT)
                            .taskStoreResult(Tasks.fromSupplier("Check context for VPC",
                                                                new Supplier<Task<CompletableFuture<Vpc>>>() {

                                @Autowired
                                private GenericApplicationContext applicationContext;

                                @Override
                                public Task<CompletableFuture<Vpc>> get() {
                                    Vpc vpc = null;
                                    try {
                                        vpc = applicationContext.getBean(Vpc.class);
                                    } catch (NoSuchBeanDefinitionException e) {
                                        // swallow
                                    }

                                    if(vpc == null){
                                        return getVpc(domainName);
                                    }else{
                                        return Tasks.noop("Vpc already exists");
                                    }
                                }
                            }));
    }

    public Task<CompletableFuture<String>> getSecurityGroupId(Vpc vpc,
                                                              String securityGroupName){
        return Tasks.fromSupplier("Get security group id for "+securityGroupName,
                                  () -> {
                                               Filter groupNameFilter = Filter.builder()
                                                                              .name("group-name")
                                                                              .values(securityGroupName)
                                                                              .build();

                                               Filter vpcIdFilter = Filter.builder()
                                                                          .name("vpc-id")
                                                                          .values(vpc.vpcId())
                                                                          .build();

                                               DescribeSecurityGroupsRequest request = DescribeSecurityGroupsRequest.builder()
                                                                                                                    .filters(groupNameFilter, vpcIdFilter)
                                                                                                                    .build();
                                               return ec2AsyncClient.describeSecurityGroups(request)
                                                                    .thenApply(response -> {
                                                                        if(!response.hasSecurityGroups()){
                                                                            throw new IllegalArgumentException("No security group can be found with the name "+securityGroupName);
                                                                        }
                                                                        return response.securityGroups().get(0).groupId();
                                                                    });
                                           });
    }

    public JobDefinition ensureSecurityGroupIdInContext(String domainName,
                                                        String securityGroupName,
                                                        String propertyName){
        return JobDefinition.create("Ensure Security Group Id In Scope for Security Group "+securityGroupName, JobScope.PARENT)
                            .jobDefinition(ensureVpcInContext(domainName))
                            .taskStoreResult(Tasks.fromSupplier("Check Context for Security Group",
                                                                new Supplier<Task<CompletableFuture<String>>>() {

                                @Autowired
                                private Vpc vpc;

                                @Autowired
                                private GenericApplicationContext context;

                                @Override
                                public Task<CompletableFuture<String>> get() {
                                    String propertyValue = ContextUtils.getProperty(propertyName, context);
                                    if(propertyValue == null){
                                        return getSecurityGroupId(vpc, securityGroupName);
                                    }else{
                                        return Tasks.noop(propertyName + " already exists in context");
                                    }
                                }
                            }), propertyName);

    }

    public Task<CompletableFuture<String>> getAcmCertificateArn(String domainName){
        return Tasks.fromSupplier("Get ACM Certificate ARN for domain "+domainName,
                                  () -> acmAsyncClient.listCertificates()
                                                               .thenApply(response -> {
                                                                   Validate.isTrue(response.hasCertificateSummaryList(), "No ACM certificates found");
                                                                   String ret = null;
                                                                   for(CertificateSummary certificate : response.certificateSummaryList()){
                                                                       if(certificate.domainName().equals(domainName)){
                                                                           ret = certificate.certificateArn();
                                                                           break;
                                                                       }
                                                                   }
                                                                   Validate.notNull(ret, "No certificate could be found matching the domain "+domainName);
                                                                   return ret;
                                                               }));
    }

    public JobDefinition ensureAcmCertificateArnInContext(String domainName,
                                                          String propertyName){
        return JobDefinition.create("Ensure ACM Certificate in scope domain: "+domainName +" propertyName: "+propertyName, JobScope.PARENT)
                            .taskStoreResult(Tasks.fromSupplier(new Supplier<Task<CompletableFuture<String>>>() {

                                @Autowired
                                private GenericApplicationContext context;

                                @Override
                                public Task<CompletableFuture<String>> get() {
                                    String propertyValue = ContextUtils.getProperty(propertyName, context);
                                    if(propertyValue == null){
                                        return getAcmCertificateArn(domainName);
                                    }else{
                                        return Tasks.noop(propertyName + " already exists in context");
                                    }
                                }
                            }), propertyName);
    }

    private Task<CompletableFuture<ModifyVpcAttributeResponse>> enableVpcDNSHostnames(){
        return Tasks.fromSupplier("Enable VPC DNS Hostnames",
                                  new Supplier<>() {

                                               @Autowired
                                               private Vpc vpc;

                                               @Override
                                               public CompletableFuture<ModifyVpcAttributeResponse> get() {
                                                   ModifyVpcAttributeRequest request =
                                                           ModifyVpcAttributeRequest.builder()
                                                                                    .enableDnsHostnames(e -> e.value(true))
                                                                                    .vpcId(vpc.vpcId())
                                                                                    .build();

                                                   return ec2AsyncClient.modifyVpcAttribute(request);
                                               }
                                           });
    }

    private Task<CompletableFuture<String>> createSecurityGroup(String securityGroupName, String description){
        return Tasks.fromSupplier("Create Security Group "+securityGroupName,
                                  new Supplier<>() {

                                               @Autowired
                                               private Vpc vpc;

                                               @Override
                                               public CompletableFuture<String> get() {
                                                   CreateSecurityGroupRequest request =
                                                           CreateSecurityGroupRequest.builder()
                                                                                     .groupName(securityGroupName)
                                                                                     .description(description)
                                                                                     .vpcId(vpc.vpcId())
                                                                                     .build();

                                                   return ec2AsyncClient.createSecurityGroup(request)
                                                                        .thenApply(CreateSecurityGroupResponse::groupId);
                                               }
                                           });
    }

    public Task<CompletableFuture<AuthorizeSecurityGroupIngressResponse>> authorizeAccessForSecurityGroup(String securityGroupId,
                                                                                                          Collection<IpPermission> ipPermissions){
        return Tasks.fromSupplier("Authorizing Access For Security Group " +securityGroupId,
                                  () -> {
                                               AuthorizeSecurityGroupIngressRequest request =
                                                       AuthorizeSecurityGroupIngressRequest.builder()
                                                                                           .groupId(securityGroupId)
                                                                                           .ipPermissions(ipPermissions)
                                                                                           .build();

                                               return ec2AsyncClient.authorizeSecurityGroupIngress(request);
                                           });
    }

    public Task<CompletableFuture<List<AvailabilityZone>>> describeAvailabilityZones(){
        return Tasks.fromSupplier("Describe Availability Zones",
                                  () -> {
                                               DescribeAvailabilityZonesRequest request =
                                                       DescribeAvailabilityZonesRequest.builder()
                                                                                       .filters(Filter.builder().name("state").values("available").build())
                                                                                       .build();

                                               return ec2AsyncClient.describeAvailabilityZones(request)
                                                                    .thenApply(DescribeAvailabilityZonesResponse::availabilityZones);
                                           });
    }

    public Task<CompletableFuture<Subnet>> createSubnet(String vpcId, String availabilityZoneId, String cidrBlock){
        return Tasks.fromSupplier("Creating Subnet vpcId: " + vpcId + " availabilityZoneId: "+ availabilityZoneId + " cidrBlock: " + cidrBlock,
                                  () -> {
                                               CreateSubnetRequest request =
                                                       CreateSubnetRequest.builder()
                                                                          .vpcId(vpcId)
                                                                          .availabilityZoneId(availabilityZoneId)
                                                                          .cidrBlock(cidrBlock)
                                                                          .build();

                                               return ec2AsyncClient.createSubnet(request).thenApply(CreateSubnetResponse::subnet);
                                           });
    }

    /**
     * Creates a single subnet for all Availability Zones within a VPC
     * @param tags to tag each subnet with.
     * @return the {@link JobDefinition} to do this work
     */
    private Task<JobDefinition> createSubnetsForAllAvailabilityZones(String vpcName, Map<String, String> tags){
        return Tasks.fromCallable("Building JobDefinition to Create Subnet for all Availability Zones",
                                  new Callable<>() {

                                      @Autowired
                                      private Vpc vpc;

                                      @Autowired
                                      private List<AvailabilityZone> availabilityZones;

                                      @Override
                                      public JobDefinition call() {
                                          // This is purposefully creating a child scope this way these subnets are not stored when this job finishes
                                          // This is done since the subnets added to  the context here will not have any tags and so must be reloaded from aws to get the tags
                                          JobDefinition ret = JobDefinition.create("Create Subnet for all Availability Zones");
                                          int i = 1;
                                          Map<String, String> tagsToUse = modNameTag(tags, vpcName+"-private");
                                          tagsToUse.put("scope", "private");

                                          // first create private subnets
                                          for(AvailabilityZone availabilityZone: availabilityZones){
                                              String subnetVarName = "privateSubnet"+i;
                                              ret.taskStoreResult(createSubnet(vpc.vpcId(), availabilityZone.zoneId(), "10.0." + i + ".0/24"), subnetVarName);
                                              ret.task(ec2TaskService.tagAwsResource("${@"+subnetVarName+".subnetId()}", tagsToUse));
                                              i++;
                                          }

                                          tagsToUse = modNameTag(tags, vpcName+"-public");
                                          tagsToUse.put("scope", "public");

                                          // now create public subnets
                                          for(AvailabilityZone availabilityZone: availabilityZones){
                                              String subnetVarName = "publicSubnet"+i;
                                              ret.taskStoreResult(createSubnet(vpc.vpcId(), availabilityZone.zoneId(), "10.0." + i + ".0/24"), subnetVarName);
                                              ret.task(ec2TaskService.tagAwsResource("${@"+subnetVarName+".subnetId()}", tagsToUse));
                                              i++;
                                          }
                                          return ret;
                                      }
                                  });
    }

    public Task<CompletableFuture<List<Subnet>>> getAllSubnetsForVpc(Vpc vpc, Map<String, String> tags){
        return Tasks.fromSupplier("Get all subnets for VPC " + vpc.vpcId(),
                                  () -> {
                                               List<Filter> filters = new ArrayList<>();

                                               Filter vpcFilter = Filter.builder()
                                                                        .name("vpc-id")
                                                                        .values(vpc.vpcId())
                                                                        .build();
                                               filters.add(vpcFilter);

                                               if(tags != null && tags.size() > 0){
                                                   Filter.Builder tagFilterBuilder = Filter.builder();
                                                   for(Map.Entry<String, String> entry : tags.entrySet()){
                                                       tagFilterBuilder.name("tag:"+entry.getKey());
                                                       tagFilterBuilder.values(entry.getValue());
                                                   }
                                                   filters.add(tagFilterBuilder.build());
                                               }

                                               DescribeSubnetsRequest request = DescribeSubnetsRequest.builder()
                                                                                                      .filters(filters)
                                                                                                      .build();

                                               return ec2AsyncClient.describeSubnets(request)
                                                                    .thenApply(DescribeSubnetsResponse::subnets);
                                           });
    }

    public JobDefinition ensureSubnetsInContext(String domainName, Map<String, String> tags){
        return JobDefinition.create("Ensure Subnets In Scope", JobScope.PARENT)
                            .jobDefinition(ensureVpcInContext(domainName))
                            .taskStoreResult(Tasks.fromSupplier("Check context for Subnets",
                                                                new Supplier<Task<CompletableFuture<List<Subnet>>>>() {

                                @Autowired
                                private Vpc vpc;

                                @Autowired(required = false)
                                private List<Subnet> subnets;

                                @Override
                                public Task<CompletableFuture<List<Subnet>>> get() {
                                    if(subnets == null || subnets.size() == 0){
                                        return getAllSubnetsForVpc(vpc, tags);
                                    }else{
                                        return Tasks.noop("Subnets already exists");
                                    }
                                }
                            }));
    }

    /**
     * Creates a {@link JobDefinition} that will create a network load balancer for the domain. The VPC and subnets must already exist.
     * @param vpcDomainName to create the load balancer for such as gw1-test.kinotic.org
     * @return {@link JobDefinition} that will perform the work
     */
    public JobDefinition createNetworkLoadBalancer(String prefix, String vpcDomainName, int port){
        Validate.notBlank(prefix, "prefix must not be blank");
        Validate.notBlank(vpcDomainName, "vpcDomainName must not be blank");
        Validate.exclusiveBetween(0, 65535, port, "Port must be between 0 and 65535");

        String loadBalancerName = Names.loadBalancerName(prefix, vpcDomainName);

        return JobDefinition.create("Create and Configure Network Load Balancer "+loadBalancerName, JobScope.ISOLATED)
                            .jobDefinition(ensureVpcInContext(vpcDomainName))
                            .jobDefinition(ensureSubnetsInContext(vpcDomainName, Map.of("scope", "public")))
                            .taskStoreResult(Tasks.fromCallable("Create Network Load Balancer",
                                    new Callable<CompletableFuture<LoadBalancer>>() {

                                        @Autowired
                                        private List<Subnet> subnets;

                                        @Override
                                        public CompletableFuture<LoadBalancer> call() {
                                            List<String> subNetIds = subnets.stream()
                                                                            .map(Subnet::subnetId)
                                                                            .collect(Collectors.toList());

                                            CreateLoadBalancerRequest request =
                                                    CreateLoadBalancerRequest.builder()
                                                                             .name(loadBalancerName)
                                                                             .subnets(subNetIds)
                                                                             .ipAddressType(IpAddressType.IPV4)
                                                                             .type(LoadBalancerTypeEnum.NETWORK)
                                                                             .build();

                                            return elbAsyncClient.createLoadBalancer(request)
                                                                 .thenApply(fut -> fut.loadBalancers().get(0));
                                        }
                                    }),"loadBalancer")
                            .taskStoreResult(Tasks.fromCallable("Create Target Group for Load Balancer",
                                    new Callable<CompletableFuture<TargetGroup>>() {

                                        @Autowired
                                        private Vpc vpc;

                                        @Override
                                        public CompletableFuture<TargetGroup> call() {

                                            CreateTargetGroupRequest request =
                                                    CreateTargetGroupRequest.builder()
                                                                            .name(loadBalancerName)
                                                                            .port(port)
                                                                            .vpcId(vpc.vpcId())
                                                                            .protocol(ProtocolEnum.TCP)
                                                                            .targetType(TargetTypeEnum.IP)
                                                                            .healthCheckProtocol(ProtocolEnum.HTTP)
                                                                            .healthCheckEnabled(true)
                                                                            .build();

                                            return elbAsyncClient.createTargetGroup(request)
                                                                 .thenApply(response -> {
                                                                     Validate.isTrue(response.hasTargetGroups(), "No Target Groups returned");
                                                                     return response.targetGroups().get(0);
                                                                 });
                                        }
                                    }), "loadBalancerTargetGroup")
                            .jobDefinition(ensureAcmCertificateArnInContext(prefix + "-" + vpcDomainName, "loadBalancerCertificateArn"))
                            .task(Tasks.fromCallable("Create Listener for Load Balancer",
                                     new Callable<CompletableFuture<CreateListenerResponse>>() {

                                         @Autowired
                                         private LoadBalancer loadBalancer;

                                         @Autowired
                                         private TargetGroup targetGroup;

                                         @Value("${loadBalancerCertificateArn}")
                                         private String loadBalancerCertificateArn;

                                         @Override
                                         public CompletableFuture<CreateListenerResponse> call() {
                                             Certificate certificate = Certificate.builder()
                                                                                  .certificateArn(loadBalancerCertificateArn)
                                                                                  .build();

                                             Action forwardAction = Action.builder()
                                                                          .targetGroupArn(targetGroup.targetGroupArn())
                                                                          .type(ActionTypeEnum.FORWARD)
                                                                          .build();

                                             CreateListenerRequest request =
                                                     CreateListenerRequest.builder()
                                                                          .loadBalancerArn(loadBalancer.loadBalancerArn())
                                                                          .defaultActions(forwardAction)
                                                                          .protocol(ProtocolEnum.TLS)
                                                                          .port(443)
                                                                          .certificates(certificate)
                                                                          .build();

                                             return elbAsyncClient.createListener(request);
                                         }
                                     }));
    }


    private Task<CompletableFuture<InternetGateway>> createInternetGateway(){
        return Tasks.fromSupplier("Create Internet Gateway",
                                  () -> ec2AsyncClient.createInternetGateway().thenApply(CreateInternetGatewayResponse::internetGateway));
    }

    private Task<CompletableFuture<AttachInternetGatewayResponse>> attachInternetGatewayToVpc(){
        return Tasks.fromSupplier("Attach Internet Gateway in context to Vpc in context",
                                  new Supplier<>() {

                                               @Autowired
                                               private Vpc vpc;

                                               @Autowired
                                               private InternetGateway internetGateway;

                                               @Override
                                               public CompletableFuture<AttachInternetGatewayResponse> get() {
                                                   AttachInternetGatewayRequest request =
                                                           AttachInternetGatewayRequest.builder()
                                                                                       .internetGatewayId(internetGateway.internetGatewayId())
                                                                                       .vpcId(vpc.vpcId())
                                                                                       .build();

                                                   return ec2AsyncClient.attachInternetGateway(request);
                                               }
                                           });
    }

    private Task<CompletableFuture<RouteTable>> createRouteTable(){
        return Tasks.fromSupplier("Create Route Table",
                                  new Supplier<>() {

                                               @Autowired
                                               private Vpc vpc;

                                               @Override
                                               public CompletableFuture<RouteTable> get() {
                                                   CreateRouteTableRequest request =
                                                           CreateRouteTableRequest.builder()
                                                                                  .vpcId(vpc.vpcId())
                                                                                  .build();

                                                   return ec2AsyncClient.createRouteTable(request).thenApply(CreateRouteTableResponse::routeTable);
                                               }
                                           });
    }

    private Task<CompletableFuture<CreateRouteResponse>> createInternetGatewayRoute(){
        return Tasks.fromSupplier("Create Internet Gateway Route",
                                  new Supplier<>() {

                                               @Autowired
                                               private InternetGateway internetGateway;

                                               @Autowired
                                               private RouteTable publicRouteTable;

                                               @Override
                                               public CompletableFuture<CreateRouteResponse> get() {
                                                   CreateRouteRequest request =
                                                           CreateRouteRequest.builder()
                                                                             .destinationCidrBlock("0.0.0.0/0")
                                                                             .gatewayId(internetGateway.internetGatewayId())
                                                                             .routeTableId(publicRouteTable.routeTableId())
                                                                             .build();

                                                   return ec2AsyncClient.createRoute(request);
                                               }
                                           });
    }

    public Task<CompletableFuture<AssociateRouteTableResponse>> associateRouteTable(String routeTableId, String subnetId){
        return Tasks.fromSupplier("Associate Route Table routeTableId: " + routeTableId + " subnetId: " + subnetId,
                                  () -> {
                                               AssociateRouteTableRequest request =
                                                       AssociateRouteTableRequest.builder()
                                                                                 .routeTableId(routeTableId)
                                                                                 .subnetId(subnetId)
                                                                                 .build();
                                               return ec2AsyncClient.associateRouteTable(request);
                                           });
    }

    private Task<JobDefinition> associateRouteTablesWithAllSubnets(){
        return Tasks.fromCallable("Building JobDefinition to Associate Route Table with All Subnet's in Context",
                                  new Callable<>() {

                                      @Autowired
                                      private List<Subnet> subnets;

                                      @Autowired
                                      private RouteTable publicRouteTable;

                                      @Autowired
                                      private RouteTable privateRouteTable;

                                      @Override
                                      public JobDefinition call() {
                                          JobDefinition ret = JobDefinition.create("Associate Route Table with All Subnet's in Context",
                                                                                   JobScope.PARENT); // parent scope since we don't store anything and this removes a little extra work
                                          for(Subnet subnet: subnets){
                                              if(tagsContain("scope", "private", subnet.tags())){
                                                  ret.task(associateRouteTable(privateRouteTable.routeTableId(), subnet.subnetId()));
                                              }else if(tagsContain("scope", "public", subnet.tags())){
                                                  ret.task(associateRouteTable(publicRouteTable.routeTableId(), subnet.subnetId()));
                                              }
                                          }
                                          return ret;
                                      }
                                  });
    }


    public Task<CompletableFuture<String>> allocateElasticIp(){
        return Tasks.fromSupplier("Allocate Elastic Ip",
                                  () -> {
                                               AllocateAddressRequest request =
                                                       AllocateAddressRequest.builder()
                                                                             .domain(DomainType.VPC)
                                                                             .build();
                                               return ec2AsyncClient.allocateAddress(request)
                                                                    .thenApply(AllocateAddressResponse::allocationId);
                                           });
    }

    public Task<CompletableFuture<String>> createNatGateway(String publicSubnetId, String elasticIpAllocationId){
        return Tasks.fromSupplier("Create Nat Gateway",
                                  () -> ec2AsyncClient.createNatGateway(builder -> {
                                               builder.subnetId(publicSubnetId);
                                               builder.allocationId(elasticIpAllocationId);
                                           }).thenApply(createNatGatewayResponse -> createNatGatewayResponse.natGateway().natGatewayId()));
    }

    public Task<CompletableFuture<CreateRouteResponse>> createNatGatewayRoute(String natGatewayId, String routeTableId){
        return Tasks.fromSupplier("Create Nat Gateway Route routeTableId:" + routeTableId + " natGatewayId:" + natGatewayId ,
                                  () -> {
                                               CreateRouteRequest request =
                                                       CreateRouteRequest.builder()
                                                                         .destinationCidrBlock("0.0.0.0/0")
                                                                         .natGatewayId(natGatewayId)
                                                                         .routeTableId(routeTableId)
                                                                         .build();

                                               return ec2AsyncClient.createRoute(request);
                                           });
    }

    public Task<CompletableFuture<NatGateway>> ensureNatGatewayAvailable(String natGatewayId){
        return Tasks.fromSupplier("Ensure Nat Gateway is Available",
                                  () -> {
                                               RetryPolicy<DescribeNatGatewaysResponse> retryPolicy = new RetryPolicy<DescribeNatGatewaysResponse>()
                                                       .handleResultIf(describeNatGatewaysResponse -> {
                                                           boolean ret = true;
                                                           if(describeNatGatewaysResponse.hasNatGateways()){
                                                               NatGateway gateway = describeNatGatewaysResponse.natGateways().get(0);
                                                               if(gateway.state() == NatGatewayState.AVAILABLE){
                                                                   ret = false;
                                                               }
                                                           }
                                                           return ret;
                                                       })
                                                       .abortIf(describeNatGatewaysResponse -> {
                                                           boolean ret = false;
                                                           if(!describeNatGatewaysResponse.hasNatGateways()) {
                                                               ret = true;
                                                           }else{
                                                               NatGateway gateway = describeNatGatewaysResponse.natGateways().get(0);
                                                               if(gateway.state() == NatGatewayState.FAILED){
                                                                   ret = true;
                                                               }
                                                           }
                                                           return ret;
                                                       })
                                                       .withDelay(Duration.ofMinutes(1))
                                                       .withMaxAttempts(-1);

                                               return Failsafe.with(retryPolicy)
                                                              .getStageAsync(() -> {
                                                                  DescribeNatGatewaysRequest request =
                                                                          DescribeNatGatewaysRequest.builder()
                                                                                                    .natGatewayIds(natGatewayId)
                                                                                                    .build();
                                                                  log.debug("Requesting NatGateway "+natGatewayId+" State");
                                                                  return ec2AsyncClient.describeNatGateways(request);
                                                              }).thenApply(t -> t.natGateways().get(0));
                                           });
    }


    private JobDefinition setupContinuumSecurityGroups(String domainName){

        // We want this mutable
        Map<String, String> tags = Map.of("domain", domainName,
                                          "createdBy", "substratum",
                                          "createdOn", sdf.format(new Date()));

        return JobDefinition.create("Setup Continuum security groups for domain "+domainName)
                            .taskStoreResult(createSecurityGroup(Names.continuumClusterAwareSecurityGroupName(domainName),
                                                                 "Allows all access for members of this security group"),
                                             "clusterAwareSgId")
                            .task(ec2TaskService.tagAwsResource("${#clusterAwareSgId}", modNameTag(tags, Names.continuumClusterAwareSecurityGroupName(domainName))))
                            .task(Tasks.fromSupplier(new Supplier<Task<CompletableFuture<AuthorizeSecurityGroupIngressResponse>>>() {

                                @Value("${clusterAwareSgId}")
                                private String clusterAwareSgId;

                                @Override
                                public Task<CompletableFuture<AuthorizeSecurityGroupIngressResponse>> get() {
                                    return authorizeAccessForSecurityGroup(clusterAwareSgId, AwsTasks.allAccessPermissionsForSecurityGroup(clusterAwareSgId));
                                }
                            }))
                            .taskStoreResult(createSecurityGroup(Names.continuumGatewaySecurityGroupName(domainName),
                                                                 "Allows all access to continuum gateway ports"),
                                             "gatewaySgId")
                            .task(ec2TaskService.tagAwsResource("${#gatewaySgId}", modNameTag(tags, Names.continuumGatewaySecurityGroupName(domainName))))
                            .task(Tasks.fromSupplier(new Supplier<Task<CompletableFuture<AuthorizeSecurityGroupIngressResponse>>>() {

                                @Value("${gatewaySgId}")
                                private String gatewaySgId;

                                @Override
                                public Task<CompletableFuture<AuthorizeSecurityGroupIngressResponse>> get() {
                                    IpRange allIps = IpRange.builder().description("From Anywhere").cidrIp("0.0.0.0/0").build();
                                    List<IpPermission> ipPermissions = new ArrayList<>(2);
                                    ipPermissions.add(IpPermission.builder()
                                                                  .ipProtocol("tcp")
                                                                  .fromPort(58503)
                                                                  .toPort(58504)
                                                                  .ipRanges(allIps)
                                                                  .build());

                                    return authorizeAccessForSecurityGroup(gatewaySgId, ipPermissions);
                                }
                            }))
                            .taskStoreResult(createSecurityGroup(Names.continuumIamSecurityGroupName(domainName),
                                                                 "Security for the Continuum IAM service"),
                                             "iamSgId")
                            .task(ec2TaskService.tagAwsResource("${#iamSgId}", modNameTag(tags, Names.continuumIamSecurityGroupName(domainName))))
                            .taskStoreResult(createSecurityGroup(Names.continuumIamDatabaseSecurityGroupName(domainName),
                                                                 "Security for the Continuum IAM RDS DB"),
                                             "iamDbSgId")
                            .task(ec2TaskService.tagAwsResource("${#iamDbSgId}", modNameTag(tags, Names.continuumIamDatabaseSecurityGroupName(domainName))))
                            .task(Tasks.fromSupplier(new Supplier<Task<CompletableFuture<AuthorizeSecurityGroupIngressResponse>>>() {

                                @Value("${iamSgId}")
                                private String iamSgId;

                                @Value("${iamDbSgId}")
                                private String iamDbSgId;

                                @Override
                                public Task<CompletableFuture<AuthorizeSecurityGroupIngressResponse>> get() {
                                    List<IpPermission> ipPermissions = new ArrayList<>(1);
                                    ipPermissions.add(IpPermission.builder()
                                                                  .ipProtocol("tcp")
                                                                  .fromPort(5432)
                                                                  .toPort(5432)
                                                                  .userIdGroupPairs(UserIdGroupPair.builder().groupId(iamSgId).build())
                                                                  .build());

                                    return authorizeAccessForSecurityGroup(iamDbSgId, ipPermissions);
                                }
                            }))
                            .taskStoreResult(createSecurityGroup(Names.supportAccessSecurityGroupName(domainName),
                                                                 "Provides no access by default but allows support users to add their access here for servers ect"),
                                             "supportAccessSgId")
                            .task(ec2TaskService.tagAwsResource("${#supportAccessSgId}", modNameTag(tags, Names.supportAccessSecurityGroupName(domainName))));

    }

    private JobDefinition setupContinuumNetworking(String domainName){
        String vpcName = Names.vpcName(domainName);

        // We want this mutable
        Map<String, String> tags = Map.of("domain", domainName,
                                          "createdBy", "substratum",
                                          "createdOn", sdf.format(new Date()));

        return JobDefinition.create("Setup Continuum networking for domain "+domainName)
                            .taskStoreResult(createInternetGateway(), "internetGateway")
                            .task(ec2TaskService.tagAwsResource("${@internetGateway.internetGatewayId()}", modNameTag(tags,vpcName+"-igw")))
                            .task(attachInternetGatewayToVpc())
                            .taskStoreResult(createRouteTable(), "publicRouteTable")
                            .task(ec2TaskService.tagAwsResource("${@publicRouteTable.routeTableId()}", modNameTag(tags,vpcName+"-rtb-public")))
                            .task(createInternetGatewayRoute())
                            .taskStoreResult(createRouteTable(), "privateRouteTable")
                            .task(ec2TaskService.tagAwsResource("${@privateRouteTable.routeTableId()}", modNameTag(tags,vpcName+"-rtb-private")))
                            .taskStoreResult(describeAvailabilityZones())
                            .task(createSubnetsForAllAvailabilityZones(vpcName, tags))
                            .jobDefinition(ensureSubnetsInContext(domainName, null))
                            .taskStoreResult(allocateElasticIp(), "natGatewayElasticIpAllocationId")
                            .taskStoreResult(Tasks.fromSupplier("Build task create NatGateway for subnets",
                                                                new Supplier<Task<CompletableFuture<String>>>() {

                                @Value("${natGatewayElasticIpAllocationId}")
                                private String allocationId;

                                @Autowired
                                private List<Subnet> subnets;

                                @Override
                                public Task<CompletableFuture<String>> get() {
                                    String publicSubnetId = null;
                                    for(Subnet subnet : subnets){
                                        if(tagsContain("scope", "public", subnet.tags())){
                                            publicSubnetId = subnet.subnetId();
                                            break;
                                        }
                                    }
                                    if(publicSubnetId == null){
                                        throw new IllegalStateException("No Public subnet could be found within the existing scope");
                                    }
                                    return createNatGateway(publicSubnetId, allocationId);
                                }
                            }), "natGatewayId")
                            .taskStoreResult(Tasks.fromSupplier("Build task Ensure Nat Gateway is Available",
                                                                new Supplier<Task<CompletableFuture<NatGateway>>>() {

                                @Value("${natGatewayId}")
                                private String natGatewayId;

                                @Override
                                public Task<CompletableFuture<NatGateway>> get() {
                                    return ensureNatGatewayAvailable(natGatewayId);
                                }
                            }), "natGateway")
                            .task(Tasks.fromSupplier("Build Task Create Nat Gateway Route",
                                                     new Supplier<Task<CompletableFuture<CreateRouteResponse>>>() {

                                @Autowired
                                private NatGateway natGateway;

                                @Autowired
                                private RouteTable privateRouteTable;

                                @Override
                                public Task<CompletableFuture<CreateRouteResponse>> get() {
                                    return createNatGatewayRoute(natGateway.natGatewayId(), privateRouteTable.routeTableId());
                                }
                            }))
                            .task(associateRouteTablesWithAllSubnets());
    }


    /**
     * Creates a complete {@link JobDefinition} that will create a VPC supporting Continuum Apps
     * @param domainName the domain name that the VPC will be created for
     * @return the {@link JobDefinition} that can be executed
     */
    public JobDefinition createContinuumVPC(String domainName){

        String vpcName = Names.vpcName(domainName);

        // We want this mutable
        Map<String, String> tags = Map.of("Name", vpcName,
                                          "domain", domainName,
                                          "createdBy", "substratum",
                                          "createdOn", sdf.format(new Date()));

        return JobDefinition.create("Create VPC for Domain "+domainName)
                            .taskStoreResult(createVpcTask(), "vpc")
                            .task(ec2TaskService.tagAwsResource("${@vpc.vpcId()}", tags))
                            .task(enableVpcDNSHostnames())
                            .jobDefinition(setupContinuumSecurityGroups(domainName))
                            .jobDefinition(setupContinuumNetworking(domainName));
    }

    private boolean tagsContain(String key, String value, List<Tag> tags){
        boolean ret = false;

        if(tags != null && tags.size() > 0){
            for (Tag tag : tags){
                if(tag.key().equals(key) && tag.value().equals(value)){
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

    private Map<String, String> modNameTag(Map<String, String> tags, String newName){
        Map<String, String> ret = new HashMap<>(tags);
        ret.put("Name", newName);
        return ret;
    }

    private boolean nameTagEquals(List<Tag> tags, String name){
        boolean ret = false;
        for(Tag tag: tags){
            if(tag.key().equals("Name") && tag.value().equals(name)){
                ret = true;
                break;
            }
        }
        return ret;
    }


}
