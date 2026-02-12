package org.kinotic.persistence.internal.idl.converters.openapi;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.kinotic.idl.api.converter.C3ConversionContext;
import org.kinotic.idl.api.converter.C3TypeConverter;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.ByteC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;

/**
 * Created By Navíd Mitchell 🤪on 2/26/25
 */
public class ArrayC3TypeTpOpenApi implements C3TypeConverter<Schema<?>, ArrayC3Type, OpenApiConversionState> {
    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof ArrayC3Type;
    }

    @Override
    public Schema<?> convert(ArrayC3Type c3Type, C3ConversionContext<Schema<?>, OpenApiConversionState> context) {
        // byte arrays are a special case per the open api spec
        // https://swagger.io/docs/specification/data-models/data-types/#string
        if(c3Type.getContains() instanceof ByteC3Type){
            return new StringSchema().format("binary");
        } else {

            Schema<?> schema = context.convert(c3Type.getContains());

            if(c3Type.getContains() instanceof ObjectC3Type){
                ObjectC3Type objectC3Type = (ObjectC3Type) c3Type.getContains();
                context.state().addReferencedSchema(objectC3Type.getName(), schema);
                schema = new Schema<>().$ref("#/components/schemas/"+objectC3Type.getName());
            }

            return new ArraySchema().items(schema);
        }
    }
}
