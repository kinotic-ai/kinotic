package org.kinotic.domain_autoconfig;

import org.kinotic.core_autoconfig.KinoticCoreAutoConfiguration;
import org.kinotic.domain.KinoticDomainLibrary;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.rest.ServerSurface;
import org.kinotic.domain.internal.api.rest.support.ConfiguredServerSurface;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

// before = core: this module's bean definitions (ServiceDirectoryStrategy) must be registered before
// core's scan evaluates @ConditionalOnBean on DefaultServiceDirectory
@AutoConfiguration(before = KinoticCoreAutoConfiguration.class)
@Import(KinoticDomainLibrary.class)
public class KinoticDomainAutoConfiguration {

    // Declared here rather than scanned: auto-configuration runs after the server module's own beans are
    // registered, so @ConditionalOnMissingBean sees a server's ServerSurface whatever the scan order
    @Bean
    @ConditionalOnMissingBean(ServerSurface.class)
    public ServerSurface configuredServerSurface(KinoticDomainProperties domainProperties) {
        return new ConfiguredServerSurface(domainProperties);
    }
}
