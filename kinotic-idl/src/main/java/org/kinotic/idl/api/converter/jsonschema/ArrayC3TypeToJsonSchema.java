package org.kinotic.idl.api.converter.jsonschema;

import org.kinotic.idl.api.converter.C3ConversionContext;
import org.kinotic.idl.api.converter.C3TypeConverter;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.ByteC3Type;
import org.kinotic.idl.api.schema.C3Type;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Converts an {@link ArrayC3Type} to a JSON Schema array, delegating the element type back through the context.
 * A byte array maps to a base64-encoded string.
 * Ported from the OpenAPI {@code ArrayC3TypeTpOpenApi}.
 * Created By Navíd Mitchell 🤪on 2/26/25
 */
public class ArrayC3TypeToJsonSchema implements C3TypeConverter<ObjectNode, ArrayC3Type, JsonSchemaConversionState> {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    @Override
    public ObjectNode convert(ArrayC3Type c3Type,
                              C3ConversionContext<ObjectNode, JsonSchemaConversionState> context) {

        ObjectNode ret;
        if (c3Type.getContains() instanceof ByteC3Type) {
            ret = FACTORY.objectNode().put("type", "string").put("contentEncoding", "base64");
        } else {
            ret = FACTORY.objectNode();
            ret.put("type", "array");
            ret.set("items", context.convert(c3Type.getContains()));
        }
        return ret;
    }

    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof ArrayC3Type;
    }
}
