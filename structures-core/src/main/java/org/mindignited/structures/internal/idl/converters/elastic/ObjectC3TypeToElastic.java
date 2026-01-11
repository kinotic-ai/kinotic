package org.mindignited.structures.internal.idl.converters.elastic;

import co.elastic.clients.elasticsearch._types.mapping.*;
import org.kinotic.continuum.idl.api.converter.C3ConversionContext;
import org.kinotic.continuum.idl.api.converter.C3TypeConverter;
import org.kinotic.continuum.idl.api.converter.Cacheable;
import org.kinotic.continuum.idl.api.schema.*;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;
import org.mindignited.structures.api.domain.DecoratedProperty;
import org.mindignited.structures.api.domain.idl.decorators.*;
import org.mindignited.structures.internal.utils.StructuresUtil;

/**
 * Converts a {@link ObjectC3Type} to a {@link Property}
 * Created by Navíd Mitchell 🤪 on 4/27/23.
 */
public class ObjectC3TypeToElastic implements C3TypeConverter<Property, ObjectC3Type, ElasticConversionState>, Cacheable {

    @Override
    public Property convert(ObjectC3Type objectC3Type,
                            C3ConversionContext<Property, ElasticConversionState> conversionContext) {
        ObjectProperty.Builder builder = new ObjectProperty.Builder()
                .dynamic(DynamicMapping.False);

        ElasticConversionState state = conversionContext.state();

        if(conversionContext.state().fieldDepth() == 0){
            EntityDecorator decorator = objectC3Type.findDecorator(EntityDecorator.class);
            if(decorator != null){
                state.setEntityDecorator(decorator);
            }else{
                throw new IllegalArgumentException("Structure Entity Definition must have an EntityDecorator");
            }
        }

        // now convert the rest of the properties
        for(PropertyDefinition property : objectC3Type.getProperties()){

            String fieldName = property.getName();
            C3Type type = property.getType();
            StructuresUtil.validatePropertyName(fieldName);

            // Initialize state for processing this field
            state.beginProcessingField(property);
            state.setShouldIndex(true);

            if(property.hasDecorators()){

                if(property.containsDecorator(NestedDecorator.class)) {

                    if (type instanceof ArrayC3Type arrayC3Type) {
                        if (!(arrayC3Type.getContains() instanceof ObjectC3Type)) {
                            throw new IllegalArgumentException("Nested decorator can only be applied to Arrays of Objects");
                        }
                    } else if (!(type instanceof ObjectC3Type || type instanceof UnionC3Type)) {
                        throw new IllegalArgumentException(
                                "Nested decorator can only be applied to Objects, Arrays of Objects, or Unions");
                    }

                    builder.properties(fieldName,
                                       NestedProperty.of(nb -> nb.properties(conversionContext.convert(type)
                                                                                              .object()
                                                                                              .properties()))
                                                     ._toProperty());

                } else if (property.containsDecorator(TextDecorator.class)) {

                    if(!(type instanceof StringC3Type)){
                        throw new IllegalArgumentException("Text decorator can only be applied to String fields");
                    }

                    builder.properties(fieldName, TextProperty.of(f -> f)._toProperty());

                } else if (property.containsDecorator(FlattenedDecorator.class)){

                    if(!(type instanceof ObjectC3Type || type instanceof UnionC3Type)){
                        throw new IllegalArgumentException("Flattened decorator can only be applied to Objects or Unions");
                    }

                    FlattenedDecorator decorator = property.findDecorator(FlattenedDecorator.class);
                    builder.properties(fieldName, FlattenedProperty.of(f -> f.depthLimit(decorator.getDepthLimit())
                                                                             .index(decorator.isIndex()))
                                                                   ._toProperty());
                } else {

                    // Handle other decorators that do not affect mapping
                    processDecorators(property, state, builder);

                    builder.properties(fieldName, conversionContext.convert(type));
                }
            }else{

                builder.properties(fieldName, conversionContext.convert(type));

            }

            state.endProcessingField();
        }

        // if we are done processing the root entity we need to add a tenant id field if it does not already exist.
        // it will only already exist if multitenancy is enabled with a @TenantIdDecorator
        if(conversionContext.state().fieldDepth() == 0
                && state.getEntityDecorator().getMultiTenancyType() == MultiTenancyType.SHARED
                && state.getTenantIdFieldName() == null){

            builder.properties(state.getStructuresProperties()
                                    .getTenantIdFieldName(),
                               KeywordProperty.of(f -> f)._toProperty());

        }

        return builder.build()._toProperty();
    }


    private void processDecorators(PropertyDefinition prop,
                                   ElasticConversionState state,
                                   ObjectProperty.Builder builder) {
        String jsonPath = state.getCurrentJsonPath();

        for(C3Decorator decorator: prop.getDecorators()){
            if(decorator instanceof IdDecorator
                    || decorator instanceof AutoGeneratedIdDecorator) {

                if (state.getIdFieldName() != null) {
                    throw new IllegalArgumentException("Only one Id field can be defined for the Entity, id field was already defined at: " 
                    + state.getIdFieldName() + " and is being defined again at: " + jsonPath);
                }

                if (jsonPath.contains(".")) {
                    throw new IllegalArgumentException("Id field cannot be nested, but was at: " + jsonPath);
                }
                        
                state.setIdFieldName(jsonPath);

            } else if (decorator instanceof NotIndexedDecorator) {

                state.setShouldIndex(false);

            } else if (decorator instanceof VersionDecorator) {

                if(state.getVersionFieldName() != null){
                    throw new IllegalArgumentException("Only one Version field can be defined for the Entity, version field was already defined at: " 
                    + state.getVersionFieldName() + " and is being defined again at: " + jsonPath);
                }

                if(jsonPath.contains(".")){
                    throw new IllegalArgumentException("Version field cannot be nested, but was at: " + jsonPath);
                }

                state.setVersionFieldName(jsonPath);

            } else if (decorator instanceof TenantIdDecorator) {

                if(state.getEntityDecorator().getMultiTenancyType() != MultiTenancyType.SHARED){
                    throw new IllegalArgumentException("The TenantId field can only be defined if the Entity has MultiTenancyType.SHARED set");
                }

                if(state.getTenantIdFieldName() != null){
                    throw new IllegalArgumentException("Only one TenantId field can be defined for the Entity, tenantId field was already defined at: " 
                    + state.getTenantIdFieldName() + " and is being defined again at: " + jsonPath);
                }

                if(jsonPath.contains(".")){
                    throw new IllegalArgumentException("TenantId field cannot be nested, but was at: " + jsonPath);
                }

                state.setTenantIdFieldName(jsonPath);

            } else if (decorator instanceof TimeReferenceDecorator) {

                if(state.getEntityDecorator().getEntityType() != EntityType.STREAM){
                    throw new IllegalArgumentException("The TimeReference field can only be defined if the Entity has stream set to true");
                }

                if(state.getTimeReferenceFieldName() != null){
                    throw new IllegalArgumentException("Only one TimeReference field can be defined for the Entity, timeReference field was already defined at: " 
                    + state.getTimeReferenceFieldName() + " and is being defined again at: " + jsonPath);
                }

                if(jsonPath.contains(".")){
                    throw new IllegalArgumentException("TimeReference field cannot be nested, but was at: " + jsonPath);
                }

                state.setTimeReferenceFieldName(jsonPath);

                // Add the required @timestamp field to the mapping
                builder.properties("@timestamp", DateProperty.of(f -> f)._toProperty());
            }
        }

        // TODO: We can get away from this since the only decorators we actually care about are the ones that are above
        //       Then we will also drop all of the UpsertFieldPreProcessor implementations since they only handle the above anyway
        state.getDecoratedProperties().add(new DecoratedProperty(jsonPath, prop.getDecorators()));
    }


    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof ObjectC3Type;
    }

}
