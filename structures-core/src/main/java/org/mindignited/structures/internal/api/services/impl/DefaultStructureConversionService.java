package org.mindignited.structures.internal.api.services.impl;

import co.elastic.clients.elasticsearch._types.mapping.ObjectProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.swagger.v3.oas.models.media.Schema;
import lombok.RequiredArgsConstructor;
import org.kinotic.continuum.idl.api.converter.IdlConverter;
import org.kinotic.continuum.idl.api.converter.IdlConverterFactory;
import org.mindignited.structures.api.config.StructuresProperties;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.idl.decorators.EntityType;
import org.mindignited.structures.internal.api.services.ElasticConversionResult;
import org.mindignited.structures.internal.api.services.StructureConversionService;
import org.mindignited.structures.internal.idl.converters.elastic.ElasticConversionState;
import org.mindignited.structures.internal.idl.converters.elastic.ElasticConverterStrategy;
import org.mindignited.structures.internal.idl.converters.graphql.GqlConversionState;
import org.mindignited.structures.internal.idl.converters.graphql.GqlConverterStrategy;
import org.mindignited.structures.internal.idl.converters.graphql.GqlTypeHolder;
import org.mindignited.structures.internal.idl.converters.openapi.OpenApiConversionState;
import org.mindignited.structures.internal.idl.converters.openapi.OpenApiConverterStrategy;
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
