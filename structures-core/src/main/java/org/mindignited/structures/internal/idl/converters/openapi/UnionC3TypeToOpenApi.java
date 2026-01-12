package org.mindignited.structures.internal.idl.converters.openapi;

import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.mindignited.continuum.idl.api.converter.C3ConversionContext;
import org.mindignited.continuum.idl.api.converter.C3TypeConverter;
import org.mindignited.continuum.idl.api.converter.C3TypeConverterContainer;
import org.mindignited.continuum.idl.api.converter.Cacheable;
import org.mindignited.continuum.idl.api.schema.C3Type;
import org.mindignited.continuum.idl.api.schema.ObjectC3Type;
import org.mindignited.continuum.idl.api.schema.UnionC3Type;
import org.mindignited.structures.api.domain.idl.decorators.DiscriminatorDecorator;

/**
 * {@link UnionC3TypeToOpenApi} is a {@link C3TypeConverterContainer} that converts a {@link UnionC3Type} to a {@link Schema}.
 * Created by Navíd Mitchell 🤪 on 5/27/23.
 */
public class UnionC3TypeToOpenApi implements C3TypeConverter<Schema<?>, UnionC3Type, OpenApiConversionState>, Cacheable {

    @Override
    public Schema<?> convert(UnionC3Type c3Type,
                             C3ConversionContext<Schema<?>, OpenApiConversionState> conversionContext) {

        ObjectSchema unionSchema = new ObjectSchema();

        // For union types the DiscriminatorDecorator can be on the type so capture that
        DiscriminatorDecorator discriminatorDecorator = c3Type.findDecorator(DiscriminatorDecorator.class);
        if(discriminatorDecorator != null && discriminatorDecorator.getPropertyName() != null){
            unionSchema.setDiscriminator(new Discriminator().propertyName(discriminatorDecorator.getPropertyName()));
        }

        for(ObjectC3Type objectC3Type : c3Type.getTypes()){

            Schema<?> objectSchema = conversionContext.convert(objectC3Type);

            conversionContext.state().addReferencedSchema(objectC3Type.getName(), objectSchema);

            String refPath = "#/components/schemas/"+objectC3Type.getName();

            unionSchema.addOneOfItem(new Schema<>().$ref(refPath));
        }

        return unionSchema;
    }

    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof UnionC3Type;
    }

}
