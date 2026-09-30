

package org.kinotic.domain.internal.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.internal.utils.MetaUtil;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.persistence.idl.PageC3Type;
import org.kinotic.domain.api.model.persistence.idl.PageableC3Type;
import org.kinotic.domain.api.model.persistence.idl.QueryOptionsC3Type;
import org.kinotic.domain.api.model.persistence.idl.TenantSelectionC3Type;
import org.kinotic.domain.api.model.persistence.idl.decorators.EntityDecorator;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;
import org.kinotic.domain.internal.serializer.RawJsonDeserializer;
import org.kinotic.domain.internal.serializer.RawJsonSerializer;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.classreading.MetadataReader;
import tools.jackson.core.Version;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.module.SimpleModule;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 *
 * Created by navid on 2019-07-24.
 */
@Slf4j
@Configuration
public class KinoticDomainJacksonConfig {

    @Bean
    public SimpleModule kinoticDomainModule(){
        SimpleModule ret = new SimpleModule("KinoticDomainModule", Version.unknownVersion());

        ret.addDeserializer(RawJson.class, new RawJsonDeserializer(new ObjectMapper()));
        ret.addSerializer(RawJson.class, new RawJsonSerializer());

        ret.setMixInAnnotation(Participant.class, ParticipantMixin.class);
        ret.registerSubtypes(DefaultSystemParticipant.class,
                             DefaultOrganizationParticipant.class,
                             DefaultApplicationParticipant.class);

        return ret;
    }

    @Bean
    public SimpleModule persistenceDefinitionModule(ApplicationContext applicationContext){
        SimpleModule ret = new SimpleModule("PersistenceDefinitionModule", Version.unknownVersion());

        Set<MetadataReader> decoratorMetas = MetaUtil.findClassesAssignableToType(applicationContext,
                                                                                  List.of(EntityDecorator.class.getPackageName()),
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
