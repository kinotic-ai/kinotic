package org.kinotic.idl.api.converter.jsonschema;

import org.kinotic.idl.api.schema.HasMetadata;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Stateless helpers for building and decorating the {@code tools.jackson} nodes that make up a JSON Schema.
 */
final class JsonSchemaUtils {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private JsonSchemaUtils() {
    }

    /**
     * Builds a {@code {"$ref":"#/$defs/<definitionName>"}} node pointing at a definition in the schema's {@code $defs}.
     */
    static ObjectNode ref(String definitionName) {
        ObjectNode node = FACTORY.objectNode();
        node.put("$ref", "#/$defs/" + definitionName);
        return node;
    }

    /**
     * Emits a {@code description} on the target schema from a source's {@code metadata.description}, if present.
     */
    static void applyDescription(ObjectNode target, HasMetadata source) {
        Map<String, ?> metadata = source.getMetadata();
        if (metadata != null) {
            Object description = metadata.get("description");
            if (description != null) {
                target.put("description", description.toString());
            }
        }
    }
}
