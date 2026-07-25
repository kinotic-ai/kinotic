package org.kinotic.idl.api.converter.jsonschema;

import lombok.RequiredArgsConstructor;
import org.kinotic.idl.api.converter.IdlConverter;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.decorators.NotNullC3Decorator;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the MCP {@code inputSchema} for a {@link FunctionDefinition}: a self-contained JSON Schema object whose
 * {@code properties} are the function's parameters, with {@code required} and {@code $defs} filled in as needed.
 * The result is ready to store on an MCP tool definition.
 */
@RequiredArgsConstructor
public class McpJsonSchemaGenerator {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private final IdlConverterFactory converterFactory;

    /**
     * Converts a function's parameters into a self-contained JSON Schema {@code inputSchema}.
     * @param function the function whose parameters describe the tool's input
     * @param referenceResolver maps a {@code ReferenceC3Type}'s qualified name to the {@link ObjectC3Type} it points
     *                          at; supply the complex types collected alongside the function's service (may be empty
     *                          when the function references no complex types)
     * @return the {@code inputSchema} object
     */
    public ObjectNode generateInputSchema(FunctionDefinition function, Map<String, ObjectC3Type> referenceResolver) {

        JsonSchemaConverterStrategy strategy = new JsonSchemaConverterStrategy(referenceResolver);
        IdlConverter<ObjectNode, JsonSchemaConversionState> converter = converterFactory.createConverter(strategy);

        ObjectNode inputSchema = FACTORY.objectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = FACTORY.objectNode();
        ArrayNode required = FACTORY.arrayNode();

        for (ParameterDefinition parameter : function.getParameters()) {
            ObjectNode parameterSchema = converter.convert(parameter.getType());
            JsonSchemaUtils.applyDescription(parameterSchema, parameter);
            properties.set(parameter.getName(), parameterSchema);

            if (parameter.containsDecorator(NotNullC3Decorator.class)) {
                required.add(parameter.getName());
            }
        }

        inputSchema.set("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.set("required", required);
        }

        Map<String, ObjectNode> referencedSchemas = converter.getConversionContext().state().getReferencedSchemas();
        if (!referencedSchemas.isEmpty()) {
            ObjectNode defs = FACTORY.objectNode();
            // sorted so output is stable regardless of the order definitions were discovered during conversion
            new TreeMap<>(referencedSchemas).forEach(defs::set);
            inputSchema.set("$defs", defs);
        }

        return inputSchema;
    }

}
