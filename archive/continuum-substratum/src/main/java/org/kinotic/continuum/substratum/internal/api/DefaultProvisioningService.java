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

package org.kinotic.continuum.substratum.internal.api;

import org.kinotic.continuum.substratum.api.ProvisioningService;
import org.kinotic.continuum.substratum.internal.tasks.DefaultKafkaTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.Predicate;

/**
 *
 * Created by Navid Mitchell on 11/12/20
 */
@Component
public class DefaultProvisioningService implements ProvisioningService {

    @Autowired
    private DefaultKafkaTaskService kafkaTaskService;

    @Autowired
    private JobService jobService;

    @Override
    public JobDefinition describeProvisionKafka(String domainName) {
        return kafkaTaskService.provisionKafkaCluster(domainName,
                                                                   "kafka.m5.large");
    }

    @Override
    public Flux<Result<?>> provisionKafka(String domainName) {
        JobDefinition job = kafkaTaskService.provisionKafkaCluster(domainName,
                                                                   "kafka.m5.large");

        return jobService.assemble(job, new ResultOptions(DiagnosticLevel.TRACE, true))
                         .filter(new NoValuesPredicate());
    }

    static class NoValuesPredicate implements Predicate<Result<?>> {

        @Override
        public boolean test(Result<?> result) {
            return result.getResultType() != ResultType.VALUE;
        }
    }
}
