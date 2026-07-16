package org.kinotic.idl.api.converter.jsonschema;

import org.kinotic.idl.api.converter.C3TypeConverter;
import org.kinotic.idl.api.converter.C3TypeConverterContainer;
import org.kinotic.idl.api.converter.IdlConverterStrategy;
import org.kinotic.idl.api.schema.*;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link IdlConverterStrategy} that converts Continuum IDL {@link C3Type}s to JSON Schema {@link ObjectNode}s for MCP
 * tool input schemas, porting the OpenAPI {@code OpenApiConverterStrategy} primitive/enum/date mapping table onto
 * {@code tools.jackson} nodes. {@link #initialState()} returns a fresh state per conversion, so each function's schema
 * is built independently.
 * Created by Navíd Mitchell 🤪 on 5/14/23.
 */
public class JsonSchemaConverterStrategy implements IdlConverterStrategy<ObjectNode, JsonSchemaConversionState> {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private static final Set<C3TypeConverter<ObjectNode, ? extends C3Type, JsonSchemaConversionState>> CONVERTERS;

    static {
        CONVERTERS = new LinkedHashSet<>(List.of(primitiveContainer(),
                                                 new ArrayC3TypeToJsonSchema(),
                                                 new MapC3TypeToJsonSchema(),
                                                 new ObjectC3TypeToJsonSchema(),
                                                 new UnionC3TypeToJsonSchema(),
                                                 new ReferenceC3TypeToJsonSchema()));
    }

    private final Map<String, ObjectC3Type> referenceResolver;

    public JsonSchemaConverterStrategy(Map<String, ObjectC3Type> referenceResolver) {
        this.referenceResolver = referenceResolver;
    }

    @Override
    public Set<C3TypeConverter<ObjectNode, ? extends C3Type, JsonSchemaConversionState>> converters() {
        return CONVERTERS;
    }

    @Override
    public JsonSchemaConversionState initialState() {
        return new JsonSchemaConversionState(referenceResolver);
    }

    @Override
    public boolean shouldCache() {
        return false;
    }

    private static C3TypeConverterContainer<ObjectNode, JsonSchemaConversionState> primitiveContainer() {
        C3TypeConverterContainer<ObjectNode, JsonSchemaConversionState> container = new C3TypeConverterContainer<>();
        container.addConverter(BooleanC3Type.class, (c3Type, context) -> FACTORY.objectNode().put("type", "boolean"))
                 .addConverter(ByteC3Type.class, (c3Type, context) -> FACTORY.objectNode()
                                                                             .put("type", "integer")
                                                                             .put("minimum", (int) Byte.MIN_VALUE)
                                                                             .put("maximum", (int) Byte.MAX_VALUE))
                 .addConverter(ShortC3Type.class, (c3Type, context) -> FACTORY.objectNode()
                                                                              .put("type", "integer")
                                                                              .put("minimum", (int) Short.MIN_VALUE)
                                                                              .put("maximum", (int) Short.MAX_VALUE))
                 .addConverter(CharC3Type.class, (c3Type, context) -> FACTORY.objectNode()
                                                                             .put("type", "string")
                                                                             .put("minLength", 1)
                                                                             .put("maxLength", 1))
                 .addConverter(DoubleC3Type.class, (c3Type, context) -> FACTORY.objectNode()
                                                                               .put("type", "number")
                                                                               .put("format", "double"))
                 .addConverter(FloatC3Type.class, (c3Type, context) -> FACTORY.objectNode()
                                                                              .put("type", "number")
                                                                              .put("format", "float"))
                 .addConverter(IntC3Type.class, (c3Type, context) -> FACTORY.objectNode()
                                                                            .put("type", "integer")
                                                                            .put("format", "int32"))
                 .addConverter(LongC3Type.class, (c3Type, context) -> FACTORY.objectNode()
                                                                             .put("type", "integer")
                                                                             .put("format", "int64"))
                 .addConverter(StringC3Type.class, (c3Type, context) -> FACTORY.objectNode().put("type", "string"))
                 .addConverter(DateC3Type.class, (c3Type, context) -> FACTORY.objectNode()
                                                                             .put("type", "string")
                                                                             .put("format", "date-time"))
                 .addConverter(EnumC3Type.class, (c3Type, context) -> {
                     ObjectNode node = FACTORY.objectNode().put("type", "string");
                     ArrayNode values = FACTORY.arrayNode();
                     for (String value : c3Type.getValues()) {
                         values.add(value);
                     }
                     node.set("enum", values);
                     return node;
                 });
        return container;
    }

}
