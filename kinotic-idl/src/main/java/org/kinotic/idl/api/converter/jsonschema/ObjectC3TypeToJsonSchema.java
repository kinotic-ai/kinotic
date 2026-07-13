package org.kinotic.idl.api.converter.jsonschema;

import org.kinotic.idl.api.converter.C3ConversionContext;
import org.kinotic.idl.api.converter.C3TypeConverter;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.PropertyDefinition;
import org.kinotic.idl.api.schema.decorators.NotNullC3Decorator;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Converts an {@link ObjectC3Type} to a JSON Schema object node.
 * Complex property types arrive as {@code ReferenceC3Type}s (see {@code SchemaFactory}) and are emitted as
 * {@code $ref}s by {@link ReferenceC3TypeToJsonSchema}, so this converter simply iterates properties and delegates
 * each type back through the context.
 * Ported from the OpenAPI {@code ObjectC3TypeToOpenApi}.
 * Created by Navíd Mitchell 🤪 on 5/15/23.
 */
public class ObjectC3TypeToJsonSchema implements C3TypeConverter<ObjectNode, ObjectC3Type, JsonSchemaConversionState> {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    @Override
    public ObjectNode convert(ObjectC3Type objectC3Type,
                              C3ConversionContext<ObjectNode, JsonSchemaConversionState> conversionContext) {

        ObjectNode schema = FACTORY.objectNode();
        schema.put("type", "object");

        ObjectNode properties = FACTORY.objectNode();
        ArrayNode required = FACTORY.arrayNode();

        for (PropertyDefinition property : objectC3Type.getProperties()) {

            ObjectNode propertySchema = conversionContext.convert(property.getType());
            properties.set(property.getName(), propertySchema);

            if (property.containsDecorator(NotNullC3Decorator.class)) {
                required.add(property.getName());
            }
        }

        schema.set("properties", properties);
        if (!required.isEmpty()) {
            schema.set("required", required);
        }

        return schema;
    }

    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof ObjectC3Type;
    }
}
