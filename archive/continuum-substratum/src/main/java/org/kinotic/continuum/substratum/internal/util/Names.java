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

package org.kinotic.continuum.substratum.internal.util;

/**
 * Util class to create various names from a domain name.
 * This is used to provide consistency in naming across various internal classes
 *
 * Created by Navid Mitchell on 7/6/20
 */
public class Names {

    public static String keyPairName(String domainName){
        return domainName.replace(".","-").trim();
    }

    public static String ecsClusterName(String domainName){
        return domainName.replace(".","-").trim();
    }

    public static String ecsContainerInstanceName(String domainName, String continuumType){
        return domainName.replace(".","-").trim() + "-ecs-"+ continuumType;
    }

    public static String mskClusterName(String domainName){
        return domainName.replace(".","-").trim();
    }

    public static String mskClusterConfigName(String domainName){
        return domainName.replace(".","-").trim() + "-config";
    }

    public static String vpcName(String domainName){
        return domainName.replace(".","-").trim();
    }

    public static String loadBalancerName(String prefix, String vpcDomainName){
        return prefix + "-" + vpcDomainName.replace(".","-").trim();
    }

    public static String continuumGatewaySecurityGroupName(String domainName){
        return vpcName(domainName) +  "-sg-gateway";
    }

    public static String supportAccessSecurityGroupName(String domainName){
        return vpcName(domainName) +  "-sg-support-access";
    }

    public static String continuumIamSecurityGroupName(String domainName){
        return (domainName) +  "-sg-iam";
    }

    public static String continuumIamDatabaseSecurityGroupName(String domainName){
        return vpcName(domainName) +  "-sg-iam-db";
    }

    public static String continuumIamDatabaseSubnetGroupName(String domainName){
        return vpcName(domainName) +  "-subnet-group-iam-db";
    }

    public static String continuumClusterAwareSecurityGroupName(String domainName){
        return vpcName(domainName) +  "-sg-cluster-aware";
    }

    public static String continuumConfigurationS3BucketName(String domainName){
        return "continuum-fs-"+domainName.replace(".","-").trim()+"-configuration";
    }

    public static String continuumDataVolumeEBSName(String domainName, String serviceName){
        return "continuum-fs-" + domainName.replace(".","-").trim() + "-" + serviceName + "-data";
    }

    public static String continuumEcrImageName(String awsAccountId, String awsRegion, String ecrRepositoryName){
        return awsAccountId + ".dkr.ecr." + awsRegion + ".amazonaws.com/" + ecrRepositoryName + ":latest";
    }

    public static String continuumEcrRepositoryName(String gitBranch, String continuumProject){
        return "continuum/" + gitBranch + "/" + continuumProject;
    }

    public static String continuumTaskName(String gitBranch, String serviceName){
        return serviceName + "-" + gitBranch;
    }

    public static String continuumRdsName(String domainName, String suffix){
        return domainName.replace(".","-").trim() + "-db-" +suffix;
    }

}
