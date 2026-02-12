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

package org.kinotic.rpc.internal.config;

import org.apache.ignite.cache.CacheAtomicityMode;
import org.kinotic.rpc.api.config.ContinuumProperties;
import org.kinotic.rpc.api.security.SessionMetadata;
import org.kinotic.rpc.internal.core.api.security.DefaultSessionMetadata;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by navid on 10/15/19
 */
@Configuration
@ConditionalOnProperty(
        value="continuum.disableClustering",
        havingValue = "false",
        matchIfMissing = true)
public class ContinuumIgniteConfigCaches {

    private final ContinuumProperties continuumProperties;

    public ContinuumIgniteConfigCaches(ContinuumProperties continuumProperties) {
        this.continuumProperties = continuumProperties;
    }

    @Bean
    public CacheConfiguration<String, SessionMetadata> sessionCache(){
        // NOTE: Key is the session id
        CacheConfiguration<String, SessionMetadata> cacheConfiguration = new CacheConfiguration<>();
        cacheConfiguration.setName(IgniteCacheConstants.SESSION_CACHE_NAME);
        cacheConfiguration.setCacheMode(CacheMode.PARTITIONED);
        cacheConfiguration.setBackups(1);
        cacheConfiguration.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
        cacheConfiguration.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(new Duration(TimeUnit.MILLISECONDS,
                                                                                             continuumProperties.getSessionTimeout())));
        cacheConfiguration.setSqlSchema("PUBLIC");
        cacheConfiguration.setIndexedTypes(String.class, DefaultSessionMetadata.class);

        return cacheConfiguration;
    }

    // Cache configs below come from the vertx cluster manager default-ignite.json we do this here so we can modify the ignite config as well.
    @Bean
    CacheConfiguration<?, ?> vertxCacheConfigTemplate(){
        CacheConfiguration<?, ?> cacheConfiguration = new CacheConfiguration<>();
        cacheConfiguration.setName("__vertx.*");
        cacheConfiguration.setCacheMode(CacheMode.REPLICATED);
        cacheConfiguration.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        cacheConfiguration.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);

        return cacheConfiguration;
    }

    @Bean
    CacheConfiguration<?, ?> vertxGenericCacheConfigTemplate(){
        CacheConfiguration<?, ?> cacheConfiguration = new CacheConfiguration<>();
        cacheConfiguration.setName("*");
        cacheConfiguration.setCacheMode(CacheMode.PARTITIONED);
        cacheConfiguration.setBackups(1);
        cacheConfiguration.setReadFromBackup(false);
        cacheConfiguration.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        cacheConfiguration.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);

        return cacheConfiguration;
    }

}
