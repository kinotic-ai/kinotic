package org.kinotic.idl.api.converter.jsonschema;

import org.kinotic.idl.api.converter.C3ConversionContext;
import org.kinotic.idl.api.converter.C3TypeConverter;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.UnionC3Type;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Converts a {@link UnionC3Type} to a JSON Schema {@code oneOf} of {@code $ref}s, one per member type.
 * The OpenAPI discriminator is dropped: {@code discriminator} is OpenAPI vocabulary, {@code oneOf} is the portable
 * part.
 * Ported from the OpenAPI {@code UnionC3TypeToOpenApi}.
 * Created by Navíd Mitchell 🤪 on 5/27/23.
 */
public class UnionC3TypeToJsonSchema implements C3TypeConverter<ObjectNode, UnionC3Type, JsonSchemaConversionState> {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    @Override
    public ObjectNode convert(UnionC3Type unionC3Type,
                              C3ConversionContext<ObjectNode, JsonSchemaConversionState> conversionContext) {

        JsonSchemaConversionState state = conversionContext.state();
        ArrayNode oneOf = FACTORY.arrayNode();

        for (ObjectC3Type member : unionC3Type.getTypes()) {

            String definitionName = member.getName();
            if (state.beginDefinition(definitionName, member)) {
                state.putDefinition(definitionName, conversionContext.convert(member));
            }

            oneOf.add(JsonSchemaUtils.ref(definitionName));
        }

        ObjectNode schema = FACTORY.objectNode();
        schema.set("oneOf", oneOf);
        return schema;
    }

    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof UnionC3Type;
    }

}
