package org.kinotic.idl.api.converter.jsonschema;

import org.kinotic.idl.api.converter.C3ConversionContext;
import org.kinotic.idl.api.converter.C3TypeConverter;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.MapC3Type;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Converts a {@link MapC3Type} to an open JSON Schema object whose {@code additionalProperties} carries the value
 * schema. JSON object keys are always strings, so the map's key type is not represented.
 */
public class MapC3TypeToJsonSchema implements C3TypeConverter<ObjectNode, MapC3Type, JsonSchemaConversionState> {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    @Override
    public ObjectNode convert(MapC3Type c3Type,
                              C3ConversionContext<ObjectNode, JsonSchemaConversionState> conversionContext) {

        ObjectNode schema = FACTORY.objectNode();
        schema.put("type", "object");
        schema.set("additionalProperties", conversionContext.convert(c3Type.getValue()));
        return schema;
    }

    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof MapC3Type;
    }
}
