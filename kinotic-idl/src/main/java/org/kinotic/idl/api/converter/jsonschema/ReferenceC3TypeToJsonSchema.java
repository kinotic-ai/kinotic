package org.kinotic.idl.api.converter.jsonschema;

import org.kinotic.idl.api.converter.C3ConversionContext;
import org.kinotic.idl.api.converter.C3TypeConverter;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.ReferenceC3Type;
import tools.jackson.databind.node.ObjectNode;

/**
 * Converts a {@link ReferenceC3Type} to a JSON Schema {@code $ref}, registering the referenced type's schema in
 * {@code $defs} on first encounter.
 * {@code SchemaFactory} emits complex types as references (with the actual definitions collected separately), so this
 * is where the {@code $ref}/{@code $defs} mechanism that makes cyclic types representable actually happens.
 */
public class ReferenceC3TypeToJsonSchema implements C3TypeConverter<ObjectNode, ReferenceC3Type, JsonSchemaConversionState> {

    @Override
    public ObjectNode convert(ReferenceC3Type referenceC3Type,
                              C3ConversionContext<ObjectNode, JsonSchemaConversionState> conversionContext) {

        JsonSchemaConversionState state = conversionContext.state();
        ObjectC3Type target = state.resolveReference(referenceC3Type.getQualifiedName());
        String definitionName = target.getName();

        // reserve the slot before converting so a reference back to this type during its own conversion emits a
        // $ref instead of recursing forever
        if (state.beginDefinition(definitionName, target)) {
            state.putDefinition(definitionName, conversionContext.convert(target));
        }

        return JsonSchemaUtils.ref(definitionName);
    }

    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof ReferenceC3Type;
    }
}
