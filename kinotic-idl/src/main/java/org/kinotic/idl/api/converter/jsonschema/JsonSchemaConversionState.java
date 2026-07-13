package org.kinotic.idl.api.converter.jsonschema;

import lombok.Getter;
import org.kinotic.idl.api.schema.ObjectC3Type;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the state accumulated while converting a single function's parameters to JSON Schema.
 * Modeled on the OpenAPI {@code OpenApiConversionState}, but self-contained (no swagger types) and created fresh
 * per function so one function's {@code $defs} never leak into another's.
 * Created by Navíd Mitchell 🤪 on 5/14/23.
 */
public class JsonSchemaConversionState {

    private final Map<String, ObjectC3Type> referenceResolver;

    /**
     * The {@code $defs} accumulated for the schema, keyed by definition (simple) name. Insertion order preserved so
     * output stays stable; the generator sorts these before emission.
     */
    @Getter
    private final Map<String, ObjectNode> referencedSchemas = new LinkedHashMap<>();

    private final Map<String, ObjectC3Type> definitionSources = new HashMap<>();

    public JsonSchemaConversionState(Map<String, ObjectC3Type> referenceResolver) {
        this.referenceResolver = referenceResolver != null ? referenceResolver : Map.of();
    }

    /**
     * Resolves a {@code ReferenceC3Type}'s qualified name to the {@link ObjectC3Type} it points at.
     * @param qualifiedName the fully qualified name to resolve
     * @return the referenced {@link ObjectC3Type}
     * @throws IllegalStateException if no type is registered under the given qualified name
     */
    public ObjectC3Type resolveReference(String qualifiedName) {
        ObjectC3Type target = referenceResolver.get(qualifiedName);
        if (target == null) {
            throw new IllegalStateException("No ObjectC3Type registered for reference '" + qualifiedName + "'");
        }
        return target;
    }

    /**
     * Reserves a {@code $defs} slot for a definition, returning whether the caller should now convert and store it.
     * Reserving before conversion breaks reference cycles: a re-entrant call for the same type sees the slot taken
     * and emits a {@code $ref} instead of recursing.
     * @param definitionName the simple name the definition will occupy in {@code $defs}
     * @param source the type providing the definition
     * @return true if this is the first encounter and the caller must convert + {@link #putDefinition}; false if the
     *         definition is already present or in progress
     * @throws IllegalStateException if a distinct type already claims the same {@code $defs} name
     */
    public boolean beginDefinition(String definitionName, ObjectC3Type source) {
        ObjectC3Type existing = definitionSources.get(definitionName);
        if (existing != null) {
            if (!existing.equals(source)) {
                throw new IllegalStateException("Duplicate JSON Schema $defs name '" + definitionName
                        + "' for distinct types '" + existing.getQualifiedName()
                        + "' and '" + source.getQualifiedName() + "'");
            }
            return false;
        }
        definitionSources.put(definitionName, source);
        return true;
    }

    /**
     * Stores the converted schema for a definition previously reserved via {@link #beginDefinition}.
     * @param definitionName the simple name the definition occupies in {@code $defs}
     * @param schema the converted schema
     */
    public void putDefinition(String definitionName, ObjectNode schema) {
        referencedSchemas.put(definitionName, schema);
    }

}
