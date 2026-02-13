package org.kinotic.boot.internal.config;

import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * Created by navid on 10/15/19
 */
@Configuration
@ConditionalOnProperty(
        value="kinotic.disableClustering",
        havingValue = "false",
        matchIfMissing = true)
public class KinoticIgniteConfigCaches {

    // The cache configs below come from the vertx cluster manager default-ignite.json we do this here so we can modify the Ignite config as well.
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
