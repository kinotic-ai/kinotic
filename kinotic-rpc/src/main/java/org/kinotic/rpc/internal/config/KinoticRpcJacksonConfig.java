

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleAbstractTypeResolver;
import tools.jackson.databind.module.SimpleModule;

/**
 *
 * Created by navid on 2019-07-24.
 */
@Configuration
public class KinoticRpcJacksonConfig {

    @Bean
    public SimpleModule kinoticRpcModule(){
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

}
