

package org.kinotic.core.internal.config;

import org.kinotic.core.api.domain.RawJson;
import org.kinotic.core.api.services.crud.Page;
import org.kinotic.core.api.services.crud.Pageable;
import org.kinotic.core.api.services.crud.SearchComparator;
import org.kinotic.core.internal.serializer.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.Version;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 *
 * Created by navid on 2019-07-24.
 */
@Configuration
public class KinoticCoreJacksonConfig {

    @Bean
    public SimpleModule kinoticCoreModule(){
        SimpleModule ret = new SimpleModule("KinoticCoreModule", Version.unknownVersion());

        ret.addDeserializer(Pageable.class, new PageableDeserializer());
        ret.addSerializer(Page.class, new PageSerializer());

        ret.addDeserializer(SearchComparator.class, new SearchComparatorDeserializer());
        ret.addSerializer(SearchComparator.class, new SearchComparatorSerializer());

        ret.addDeserializer(RawJson.class, new RawJsonDeserializer(new ObjectMapper()));
        ret.addSerializer(RawJson.class, new org.kinotic.core.internal.serializer.RawJsonSerializer());

        return ret;
    }

}
