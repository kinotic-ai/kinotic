

package org.kinotic.rpc.gateway.internal.config;

import org.kinotic.rpc.api.security.SecurityService;
import org.kinotic.rpc.gateway.internal.api.security.DummySecurityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Setups correct {@link SecurityService} implementation for production use.
 *
 *
 * Created by navid on 2/10/20
 */
@Configuration
public class ContinuumGatewaySecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    SecurityService dummySecurityService(){
        return new DummySecurityService();
    }

}
