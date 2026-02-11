package org.kinotic.persistence.internal.idl.converters.openapi;

import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.kinotic.continuum.idl.api.converter.C3ConversionContext;
import org.kinotic.continuum.idl.api.converter.C3TypeConverter;
import org.kinotic.continuum.idl.api.converter.Cacheable;
import org.kinotic.continuum.idl.api.schema.*;
import org.kinotic.continuum.idl.api.schema.decorators.NotNullC3Decorator;
import org.kinotic.structures.api.domain.idl.decorators.DiscriminatorDecorator;
import org.kinotic.structures.api.domain.idl.decorators.ReadOnlyDecorator;

/**
 * Created by Navíd Mitchell 🤪 on 5/15/23.
 */
public class ObjectC3TypeToOpenApi implements C3TypeConverter<Schema<?>, ObjectC3Type, OpenApiConversionState>, Cacheable {

    @Override
    public Schema<?> convert(ObjectC3Type objectC3Type,
                             C3ConversionContext<Schema<?>, OpenApiConversionState> conversionContext) {

        ObjectSchema objectSchema = new ObjectSchema();
        objectSchema.setName(objectC3Type.getName());

        for(PropertyDefinition property : objectC3Type.getProperties()){

            String fieldName = property.getName();
            C3Type type = property.getType();

            conversionContext.state().beginProcessingField(property);

            Schema<?> fieldValue = conversionContext.convert(type);

            conversionContext.state().endProcessingField();

            if(isReadOnly(property)){
                fieldValue.setReadOnly(true);
            }

            // if this is an object we create a reference schema
            // TODO: handle cases where the same object name is used across multiple different types in the same application
            //       To handle this we will need to keep track of all "Models" per application and check for conflicts
            //       Or this could be done by keeping the Conversion State around and converting all Structures for a application at once
            if(type instanceof ComplexC3Type){
                // For union literals the DiscriminatorDecorator can be on the property, we capture that here.
                if(type instanceof UnionC3Type){
                    DiscriminatorDecorator discriminatorDecorator = property.findDecorator(DiscriminatorDecorator.class);
                    if(discriminatorDecorator != null && discriminatorDecorator.getPropertyName() != null){
                        fieldValue.setDiscriminator(new Discriminator().propertyName(discriminatorDecorator.getPropertyName()));
                    }
                }

                ComplexC3Type complexField = (ComplexC3Type) type;
                conversionContext.state().addReferencedSchema(complexField.getName(), fieldValue);
                fieldValue = new Schema<>().$ref("#/components/schemas/"+complexField.getName());
            }

            objectSchema.addProperty(fieldName, fieldValue);

            if(isRequired(property)){
                objectSchema.addRequiredItem(fieldName);
            }
        }

        return objectSchema;
    }

    private boolean isReadOnly(PropertyDefinition propertyDefinition){
        return propertyDefinition.containsDecorator(ReadOnlyDecorator.class);
    }

    private boolean isRequired(PropertyDefinition propertyDefinition){
        return propertyDefinition.containsDecorator(NotNullC3Decorator.class);
    }

    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof ObjectC3Type;
    }
}
