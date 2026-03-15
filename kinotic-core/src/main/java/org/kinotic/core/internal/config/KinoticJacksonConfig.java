package org.kinotic.core.internal.config;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.internal.serializer.PageSerializer;
import org.kinotic.core.internal.serializer.PageableDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ReactiveAdapterRegistry;
import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleModule;

/**
 *
 * Created by navid on 2019-07-24.
 */
@Configuration
@Import(JacksonAutoConfiguration.class)
public class KinoticJacksonConfig {

    @Bean
    public SimpleModule kinoticCoreModule(){
        SimpleModule ret = new SimpleModule("KinoticCoreModule", Version.unknownVersion());

        ret.addDeserializer(Pageable.class, new PageableDeserializer());
        ret.addSerializer(Page.class, new PageSerializer());

        return ret;
    }

    // FIXME: Make sure this works with Spring WebFlux
    // This is configured in org.kinotic.continuum.internal.api.DefaultContinuum
    // It is done there in case this bean is supplied by spring directly
    @ConditionalOnMissingBean
    @Bean
    public ReactiveAdapterRegistry reactiveAdapterRegistry(){
        return new ReactiveAdapterRegistry();
    }


}
