

package org.kinotic.rpc.internal.config;

import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.kinotic.boot.api.config.KinoticProperties;
import org.kinotic.rpc.api.security.SessionMetadata;
import org.kinotic.rpc.internal.api.security.DefaultSessionMetadata;
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
        value="kinotic.disableClustering",
        havingValue = "false",
        matchIfMissing = true)
public class KinoticRpcIgniteCachesConfig {

    private final KinoticProperties kinoticProperties;

    public KinoticRpcIgniteCachesConfig(KinoticProperties kinoticProperties) {
        this.kinoticProperties = kinoticProperties;
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
                                                                                             kinoticProperties.getSessionTimeout())));
        cacheConfiguration.setSqlSchema("PUBLIC");
        cacheConfiguration.setIndexedTypes(String.class, DefaultSessionMetadata.class);

        return cacheConfiguration;
    }

}
