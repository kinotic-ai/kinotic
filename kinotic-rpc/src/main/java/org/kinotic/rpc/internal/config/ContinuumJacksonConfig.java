

package org.kinotic.rpc.internal.config;

import org.kinotic.rpc.api.security.DefaultParticipant;
import org.kinotic.rpc.api.security.Participant;
import org.kinotic.rpc.api.crud.Page;
import org.kinotic.rpc.api.crud.Pageable;
import org.kinotic.rpc.api.crud.SearchComparator;
import org.kinotic.rpc.internal.serializer.PageSerializer;
import org.kinotic.rpc.internal.serializer.PageableDeserializer;
import org.kinotic.rpc.internal.serializer.SearchComparatorDeserializer;
import org.kinotic.rpc.internal.serializer.SearchComparatorSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ReactiveAdapterRegistry;
import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleAbstractTypeResolver;
import tools.jackson.databind.module.SimpleModule;

/**
 *
 * Created by navid on 2019-07-24.
 */
@Configuration
@Import(JacksonAutoConfiguration.class)
public class ContinuumJacksonConfig {

    @Bean
    public SimpleModule continuumModule(){
        SimpleModule ret = new SimpleModule("KinoticRpcModule", Version.unknownVersion());

        ret.addDeserializer(Pageable.class, new PageableDeserializer());
        ret.addSerializer(Page.class, new PageSerializer());

        ret.addDeserializer(SearchComparator.class, new SearchComparatorDeserializer());
        ret.addSerializer(SearchComparator.class, new SearchComparatorSerializer());

        SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
        resolver.addMapping(Participant.class, DefaultParticipant.class);

        ret.setAbstractTypes(resolver);

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
