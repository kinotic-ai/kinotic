package org.kinotic.domain.internal.config;

import org.kinotic.core.api.directory.DefaultServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryStrategy;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link ServiceDirectory} over this module's Elasticsearch-backed
 * {@link ServiceDirectoryStrategy}. The presence of this bean is what activates the registration path's directory
 * calls and the liveness singleton.
 */
@Configuration
public class ServiceDirectoryConfig {

    // Declared as the concrete type so ServiceLivenessUpdater's
    // @SpringResource(resourceClass = DefaultServiceDirectory.class) can resolve the bean by class
    @Bean
    public DefaultServiceDirectory serviceDirectory(ServiceDirectoryStrategy serviceDirectoryStrategy,
                                                    EventBusService eventBusService,
                                                    SchemaFactory schemaFactory,
                                                    IdlConverterFactory idlConverterFactory) {
        return new DefaultServiceDirectory(serviceDirectoryStrategy,
                                           eventBusService,
                                           schemaFactory,
                                           idlConverterFactory);
    }

}
