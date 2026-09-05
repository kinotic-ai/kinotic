package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch._types.mapping.ObjectProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import org.kinotic.idl.api.converter.IdlConverter;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.idl.decorators.EntityType;
import org.kinotic.persistence.internal.converters.elastic.ElasticConversionState;
import org.kinotic.persistence.internal.converters.elastic.ElasticConverterStrategy;
import org.springframework.stereotype.Component;

/**
 * Created by Navíd Mitchell 🤪on 5/11/23.
 */
@Component
@RequiredArgsConstructor
public class DefaultEntityDefinitionConversionService implements EntityDefinitionConversionService {

    private final IdlConverterFactory idlConverterFactory;
    private final PersistenceProperties persistenceProperties;

    @WithSpan
    @Override
    public ElasticConversionResult convertToElasticMapping(EntityDefinition entityDefinition) {
        ObjectProperty objectProperty;

        IdlConverter<Property, ElasticConversionState> converter = idlConverterFactory
                .createConverter(new ElasticConverterStrategy(persistenceProperties));

        ElasticConversionState state = converter.getConversionContext().state();

        Property esProperty = converter.convert(entityDefinition.getSchema());

        if(esProperty.isObject()){
            objectProperty = esProperty.object();
        }else{
            throw new IllegalStateException("Entity must be an object");
        }

        if(state.getIdFieldName() == null){
            throw new IllegalArgumentException("An Id field must be defined for the Entity");
        }

        if(state.getEntityDecorator().getEntityType() == EntityType.STREAM) {
            if(state.getVersionFieldName() != null) {
                throw new IllegalArgumentException("You should not provide a version field when an Entity is a stream");
            }
            if(state.getTimeReferenceFieldName() == null) {
                throw new IllegalArgumentException("You must provide a time reference field when configuring an Entity as a stream");
            }
        }

        return new ElasticConversionResult(state.getDecoratedProperties(),
                                           state.getEntityDecorator(),
                                           objectProperty,
                                           state.getVersionFieldName(),
                                           state.getTenantIdFieldName(),
                                           state.getTimeReferenceFieldName());
    }

}
