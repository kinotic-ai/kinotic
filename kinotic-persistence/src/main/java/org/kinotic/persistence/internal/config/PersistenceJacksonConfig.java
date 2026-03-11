package org.kinotic.persistence.internal.config;

import co.elastic.clients.elasticsearch._types.FieldValue;
import org.kinotic.persistence.internal.serializer.*;
import tools.jackson.core.Version;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.module.SimpleAbstractTypeResolver;
import tools.jackson.databind.module.SimpleModule;
import org.apache.commons.lang3.tuple.Pair;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.core.internal.utils.MetaUtil;
import org.kinotic.persistence.api.model.FastestType;
import org.kinotic.persistence.api.model.TenantSpecificId;
import org.kinotic.persistence.api.model.idl.PageC3Type;
import org.kinotic.persistence.api.model.idl.PageableC3Type;
import org.kinotic.persistence.api.model.idl.QueryOptionsC3Type;
import org.kinotic.persistence.api.model.idl.TenantSelectionC3Type;
import org.kinotic.persistence.api.model.DefaultTenantSpecificId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.classreading.MetadataReader;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Configuration
public class PersistenceJacksonConfig {

    private static final Logger log = LoggerFactory.getLogger(PersistenceJacksonConfig.class);

    @Bean
    public SimpleModule persistenceJacksonModule(ApplicationContext applicationContext){
        SimpleModule ret = new SimpleModule("PersistenceModule", Version.unknownVersion());

        Set<MetadataReader> decoratorMetas = MetaUtil.findClassesAssignableToType(applicationContext,
                                                                                  List.of("org.kinotic.persistence.api.model.idl.decorators"),
                                                                                  C3Decorator.class);
        // Register all C3Decorator's with Jackson
        for(MetadataReader decoratorMeta : decoratorMetas){

            if(!decoratorMeta.getClassMetadata().isAbstract()) {
                try {
                    Pair<Class<?>, String> decoratorInfo = getDecoratorInfo(decoratorMeta);

                    ret.registerSubtypes(new NamedType(decoratorInfo.getLeft(), decoratorInfo.getRight()));

                } catch (NoSuchFieldException e) {
                    log.warn("{} Could not be mapped. A public static final field named 'type' must exist on the class.",
                             decoratorMeta.getClassMetadata().getClassName());
                }
            }
        }

        // register additional needed C3Types
        ret.registerSubtypes(new NamedType(PageableC3Type.class, "pageable"));
        ret.registerSubtypes(new NamedType(PageC3Type.class, "page"));
        ret.registerSubtypes(new NamedType(TenantSelectionC3Type.class, "tenantSelection"));
        ret.registerSubtypes(new NamedType(QueryOptionsC3Type.class, "queryOptions"));

        // register internal serializer deserializers
        ret.addDeserializer(FieldValue.class, new FieldValueDeserializer());
        ret.addSerializer(FieldValue.class, new FieldValueSerializer());

        ret.addSerializer(FastestType.class, new FastestTypeSerializer());

        SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
        resolver.addMapping(TenantSpecificId.class, DefaultTenantSpecificId.class);

        ret.setAbstractTypes(resolver);

        return ret;
    }


    private Pair<Class<?>, String> getDecoratorInfo(MetadataReader metadataReader) throws NoSuchFieldException{
        try {
            Class<?> decoratorClass = Class.forName(metadataReader.getClassMetadata().getClassName());
            Field typeField = decoratorClass.getDeclaredField("type");
            String type = (String) typeField.get(null);
            return Pair.of(decoratorClass, type);
        } catch (IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
