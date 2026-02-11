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

package org.kinotic.continuum.internal.core.api.support;

/**
 *
 * Created by navid on 10/17/19
 */
public class DefaultClusterTestService implements ClusterTestService {

    private final String data;

    public DefaultClusterTestService(String data) {
        this.data = data;
    }

    @Override
    public Long getFreeMemory() {
        return 428L;
    }

    @Override
    public String getData() {
        return data;
    }
}
