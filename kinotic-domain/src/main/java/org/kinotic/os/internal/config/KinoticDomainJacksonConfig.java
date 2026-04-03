

package org.kinotic.os.internal.config;

import org.kinotic.os.api.model.RawJson;
import org.kinotic.os.internal.serializer.RawJsonDeserializer;
import org.kinotic.os.internal.serializer.RawJsonSerializer;
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
public class KinoticDomainJacksonConfig {

    @Bean
    public SimpleModule kinoticDomainModule(){
        SimpleModule ret = new SimpleModule("KinoticDomainModule", Version.unknownVersion());

        ret.addDeserializer(RawJson.class, new RawJsonDeserializer(new ObjectMapper()));
        ret.addSerializer(RawJson.class, new RawJsonSerializer());

        return ret;
    }

}
