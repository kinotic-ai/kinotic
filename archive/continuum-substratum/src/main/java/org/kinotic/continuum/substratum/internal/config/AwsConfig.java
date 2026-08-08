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

package org.kinotic.continuum.substratum.internal.config;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogsAsync;
import com.amazonaws.services.logs.AWSLogsAsyncClientBuilder;
import org.kinotic.aws.s3.DefaultS3Service;
import org.kinotic.aws.s3.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmAsyncClient;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.ecs.EcsAsyncClient;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2AsyncClient;
import software.amazon.awssdk.services.kafka.KafkaAsyncClient;
import software.amazon.awssdk.services.rds.RdsAsyncClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;

/**
 *
 * Created by Navid Mitchell on 3/24/20
 */
@Configuration
public class AwsConfig {

    @Autowired
    private ContinuumSubstratumProperties properties;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(){
        return DefaultCredentialsProvider.create();
    }

    @Bean
    S3Service s3Service(){
        return new DefaultS3Service(Regions.fromName(properties.getAwsRegion()));
    }

    @Bean(destroyMethod = "close")
    public Ec2AsyncClient ec2AsyncClient(AwsCredentialsProvider credentialsProvider){
        return Ec2AsyncClient.builder()
                             .region(Region.of(properties.getAwsRegion()))
                             .credentialsProvider(credentialsProvider)
                             .build();
    }

    @Bean(destroyMethod = "close")
    public EcsAsyncClient ecsAsyncClient(AwsCredentialsProvider credentialsProvider){
        return EcsAsyncClient.builder()
                             .region(Region.of(properties.getAwsRegion()))
                             .credentialsProvider(credentialsProvider)
                             .build();
    }


    @Bean(destroyMethod = "close")
    public KafkaAsyncClient kafkaAsyncClient(AwsCredentialsProvider credentialsProvider){
        return KafkaAsyncClient.builder()
                               .region(Region.of(properties.getAwsRegion()))
                               .credentialsProvider(credentialsProvider)
                               .build();
    }

    @Bean(destroyMethod = "close")
    public RdsAsyncClient rdsAsyncClient(AwsCredentialsProvider credentialsProvider){
        return RdsAsyncClient.builder()
                             .region(Region.of(properties.getAwsRegion()))
                             .credentialsProvider(credentialsProvider)
                             .build();
    }

    @Bean(destroyMethod = "close")
    public ElasticLoadBalancingV2AsyncClient elasticLoadBalancingV2AsyncClient(AwsCredentialsProvider credentialsProvider){
        return ElasticLoadBalancingV2AsyncClient.builder()
                                                .region(Region.of(properties.getAwsRegion()))
                                                .credentialsProvider(credentialsProvider)
                                                .build();
    }

    @Bean(destroyMethod = "close")
    public AcmAsyncClient acmAsyncClient(AwsCredentialsProvider credentialsProvider){
        return AcmAsyncClient.builder()
                             .region(Region.of(properties.getAwsRegion()))
                             .credentialsProvider(credentialsProvider)
                             .build();
    }

    @Bean()
    public AWSLogsAsync awsLogsAsyncClient(){
        return AWSLogsAsyncClientBuilder.standard()
                                        .withRegion(properties.getAwsRegion())
                                        .withCredentials(com.amazonaws.auth.DefaultAWSCredentialsProviderChain.getInstance())
                                        .build();
    }

    @Bean
    SecretsManagerAsyncClient secretsManagerClient(AwsCredentialsProvider credentialsProvider){
        return SecretsManagerAsyncClient.builder()
                                   .region(Region.of(properties.getAwsRegion()))
                                   .credentialsProvider(credentialsProvider)
                                   .build();
    }


}
