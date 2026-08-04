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
import org.kinotic.continuum.grind.api.Task;
import org.kinotic.continuum.grind.api.Tasks;
import org.kinotic.continuum.substratum.internal.util.Names;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;

import java.util.concurrent.CompletableFuture;

/**
 *
 * Created by Navid Mitchell on 4/2/20
 */
@Component
public class DefaultContinuumProvisioningService {

    private final DefaultVpcTaskService vpcTaskService;

    private final DefaultKafkaTaskService kafkaTaskService;

    private final DefaultECSTaskService ecsTaskService;

    public DefaultContinuumProvisioningService(DefaultVpcTaskService vpcTaskService,
                                               DefaultKafkaTaskService kafkaTaskService,
                                               DefaultECSTaskService ecsTaskService) {
        this.vpcTaskService = vpcTaskService;
        this.kafkaTaskService = kafkaTaskService;
        this.ecsTaskService = ecsTaskService;
    }

    public JobDefinition buildContinuumProject(String ecsBuildClusterName,
                                               String gitBranch,
                                               String continuumProject){
        Task<CompletableFuture<RunTaskResponse>> buildTask = ecsTaskService.runContinuumBuilder(ecsBuildClusterName,
                                                                                                "git@github.org:kinotic-foundation/continuum.git",
                                                                                                gitBranch,
                                                                                                continuumProject,
                                                                                                Names.continuumEcrRepositoryName(gitBranch, continuumProject));

        return JobDefinition.create().task(buildTask);
    }

    public JobDefinition createProvisionContinuumClusterJob(String domain){
        return JobDefinition.create()
                            .task(Tasks.fromValue(vpcTaskService.createContinuumVPC(domain)))
                            .task(Tasks.fromValue(kafkaTaskService.provisionKafkaCluster(domain, "kafka.t3.small")));
    }

}
