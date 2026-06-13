package org.kinotic.persistence.internal.config;

import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.persistence.api.config.KinoticPersistenceProperties;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;
import org.kinotic.persistence.internal.api.services.security.NoopAuthorizationServiceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthorizationServiceFactory authorizationServiceFactory(){
        return new NoopAuthorizationServiceFactory();
    }

    /**
     * Makes the PersistenceProperties bean available for use by other beans without needing to inject {@link KinoticProperties}
     */
    @Bean
    public PersistenceProperties persistenceProperties(KinoticPersistenceProperties kinoticPersistenceProperties){
        return kinoticPersistenceProperties.getPersistence();
    }

}
