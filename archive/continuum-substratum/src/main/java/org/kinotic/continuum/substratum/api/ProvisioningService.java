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

package org.kinotic.continuum.substratum.api;

import org.kinotic.continuum.api.annotations.Publish;
import org.kinotic.continuum.grind.api.JobDefinition;
import org.kinotic.continuum.grind.api.Result;
import reactor.core.publisher.Flux;

/**
 *
 * Created by Navid Mitchell on 11/12/20
 */
@Publish(version = "0.1.0")
public interface ProvisioningService {

    JobDefinition describeProvisionKafka(String domainName);

    Flux<Result<?>> provisionKafka(String domainName);

}
