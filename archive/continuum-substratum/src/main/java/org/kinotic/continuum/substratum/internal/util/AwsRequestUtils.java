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

import software.amazon.awssdk.services.ecs.model.*;

import java.util.Map;

/**
 *
 * Created by Navíd Mitchell 🤪 on 7/6/21.
 */
public class AwsRequestUtils {

    public static MountPoint getContinuumDomainConfigurationMountPoint(String domainName) {
        return MountPoint.builder()
                         .containerPath("/media/continuum/configuration")
                         .sourceVolume(Names.continuumConfigurationS3BucketName(domainName))
                         .build();
    }

    /**
     * Return volume containing Continuum Deploy Keys. This volume is backed by s3.
     * @return the ECS {@link Volume}
     */
    public static Volume getContinuumDeployKeysVolume() {
        DockerVolumeConfiguration deployKeysVolumeConfiguration = DockerVolumeConfiguration.builder()
                                                                                           .autoprovision(true)
                                                                                           .scope(Scope.SHARED)
                                                                                           .driver("rexray/s3fs")
                                                                                           .build();
        return Volume.builder()
                     .name("continuum-fs-deploy-keys")
                     .dockerVolumeConfiguration(deployKeysVolumeConfiguration)
                     .build();
    }

    /**
     * Return volume that is used for builder tasks to preserve data between build requests
     * @return the ECS {@link Volume}
     */
    public static Volume getContinuumBuilderWorkVolume() {
        DockerVolumeConfiguration workVolumeConfiguration = DockerVolumeConfiguration.builder()
                                                                                     .autoprovision(true)
                                                                                     .scope(Scope.SHARED)
                                                                                     .driver("rexray/ebs")
                                                                                     .driverOpts(Map.of("volumetype",
                                                                                                        "gp2",//TODO: find out how to use sc1 is used for cheapest storage
                                                                                                        "size",
                                                                                                        "100"))
                                                                                     .build();
        return Volume.builder()
                     .name("continuum-fs-builder-work")
                     .dockerVolumeConfiguration(workVolumeConfiguration)
                     .build();
    }

    public static LogConfiguration buildLogConfiguration(String logGroup, String awsRegion){
        return LogConfiguration.builder()
                               .logDriver("awslogs")
                               .options(Map.of("awslogs-group", "/continuum/ecs/"+logGroup,
                                               "awslogs-region", awsRegion,
                                               "awslogs-stream-prefix", "continuum",
                                               "awslogs-create-group", "true"))
                               .build();
    }
}
