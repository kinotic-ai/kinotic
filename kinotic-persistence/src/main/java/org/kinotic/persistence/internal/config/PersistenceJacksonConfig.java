package org.kinotic.persistence.internal.config;

import co.elastic.clients.elasticsearch._types.FieldValue;
import org.kinotic.persistence.internal.serializer.*;
import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleAbstractTypeResolver;
import tools.jackson.databind.module.SimpleModule;
import org.kinotic.persistence.api.model.FastestType;
import org.kinotic.persistence.api.model.TenantSpecificId;
import org.kinotic.persistence.api.model.DefaultTenantSpecificId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Configuration
public class PersistenceJacksonConfig {

    @Bean
    public SimpleModule persistenceJacksonModule(){
        SimpleModule ret = new SimpleModule("PersistenceModule", Version.unknownVersion());

        // register internal serializer deserializers
        ret.addDeserializer(FieldValue.class, new FieldValueDeserializer());
        ret.addSerializer(FieldValue.class, new FieldValueSerializer());

        ret.addSerializer(FastestType.class, new FastestTypeSerializer());

        SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
        resolver.addMapping(TenantSpecificId.class, DefaultTenantSpecificId.class);

        ret.setAbstractTypes(resolver);

        return ret;
    }

}
