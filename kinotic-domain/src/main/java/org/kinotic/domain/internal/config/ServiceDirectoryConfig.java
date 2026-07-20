package org.kinotic.domain.internal.config;

import org.kinotic.core.api.directory.DefaultServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryRepository;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link ServiceDirectory} over this module's Elasticsearch-backed
 * {@link ServiceDirectoryRepository}. The presence of this bean is what activates the registration path's directory
 * calls and the liveness singleton.
 */
@Configuration
public class ServiceDirectoryConfig {

    @Bean
    public ServiceDirectory serviceDirectory(ServiceDirectoryRepository serviceDirectoryRepository,
                                             EventBusService eventBusService,
                                             SchemaFactory schemaFactory,
                                             IdlConverterFactory idlConverterFactory) {
        return new DefaultServiceDirectory(serviceDirectoryRepository,
                                           eventBusService,
                                           schemaFactory,
                                           idlConverterFactory);
    }

}
