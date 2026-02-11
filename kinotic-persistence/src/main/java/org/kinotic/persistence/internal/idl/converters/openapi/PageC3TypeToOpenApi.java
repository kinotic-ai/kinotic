package org.kinotic.persistence.internal.idl.converters.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import org.kinotic.continuum.idl.api.converter.C3ConversionContext;
import org.kinotic.continuum.idl.api.converter.C3TypeConverter;
import org.kinotic.continuum.idl.api.schema.C3Type;
import org.kinotic.structures.api.domain.idl.PageC3Type;
import org.kinotic.persistence.internal.utils.OpenApiUtils;

/**
 * Converts a {@link PageC3Type} to an OpenApi {@link Schema}
 * Created by Navíd Mitchell 🤪 on 5/7/24.
 */
public class PageC3TypeToOpenApi implements C3TypeConverter<Schema<?>, PageC3Type, OpenApiConversionState> {
    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof PageC3Type;
    }

    @Override
    public Schema<?> convert(PageC3Type c3Type, C3ConversionContext<Schema<?>, OpenApiConversionState> conversionContext) {

        String contentTypeName = c3Type.getContentType().getName();
        Schema<?> contentSchema = conversionContext.convert(c3Type.getContentType());
        conversionContext.state().addReferencedSchema(contentTypeName, contentSchema);

        Schema<?> refSchema = new Schema<>().$ref(Components.COMPONENTS_SCHEMAS_REF + contentTypeName);
        return OpenApiUtils.createPageSchema(refSchema);
    }
}
