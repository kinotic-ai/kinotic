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

import org.kinotic.rpc.api.security.DefaultParticipant;
import org.kinotic.rpc.api.security.Participant;
import org.kinotic.continuum.api.crud.Page;
import org.kinotic.continuum.api.crud.Pageable;
import org.kinotic.continuum.api.crud.SearchComparator;
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
        SimpleModule ret = new SimpleModule("ContinuumModule", Version.unknownVersion());

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
