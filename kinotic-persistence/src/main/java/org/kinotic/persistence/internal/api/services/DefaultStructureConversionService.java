package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch._types.mapping.ObjectProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.swagger.v3.oas.models.media.Schema;
import lombok.RequiredArgsConstructor;
import org.kinotic.idl.api.converter.IdlConverter;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.idl.decorators.EntityType;
import org.kinotic.persistence.internal.idl.converters.elastic.ElasticConversionState;
import org.kinotic.persistence.internal.idl.converters.elastic.ElasticConverterStrategy;
import org.kinotic.persistence.internal.idl.converters.graphql.GqlConversionState;
import org.kinotic.persistence.internal.idl.converters.graphql.GqlConverterStrategy;
import org.kinotic.persistence.internal.idl.converters.graphql.GqlTypeHolder;
import org.kinotic.persistence.internal.idl.converters.openapi.OpenApiConversionState;
import org.kinotic.persistence.internal.idl.converters.openapi.OpenApiConverterStrategy;
import org.springframework.stereotype.Component;

/**
 * Created by Navíd Mitchell 🤪on 5/11/23.
 */
@Component
@RequiredArgsConstructor
public class DefaultStructureConversionService implements StructureConversionService {

    private final IdlConverterFactory idlConverterFactory;
    private final StructuresProperties structuresProperties;

    @WithSpan
    @Override
    public ElasticConversionResult convertToElasticMapping(Structure structure) {
        ObjectProperty objectProperty;

        IdlConverter<Property, ElasticConversionState> converter = idlConverterFactory
                .createConverter(new ElasticConverterStrategy(structuresProperties));

        ElasticConversionState state = converter.getConversionContext().state();

        Property esProperty = converter.convert(structure.getEntityDefinition());

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

    @Override
    public IdlConverter<GqlTypeHolder, GqlConversionState> createGqlConverter() {
        return idlConverterFactory.createConverter(new GqlConverterStrategy(structuresProperties));
    }

    @Override
    public IdlConverter<Schema<?>, OpenApiConversionState> createOpenApiConverter(){
        return idlConverterFactory.createConverter(new OpenApiConverterStrategy(structuresProperties));
    }

}
