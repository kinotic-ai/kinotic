package org.kinotic.idl.api.converter.jsonschema;

import org.junit.jupiter.api.Test;
import org.kinotic.idl.api.schema.AnyC3Type;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.ByteC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.IntC3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.ParameterDefinition;
import org.kinotic.idl.api.schema.PropertyDefinition;
import org.kinotic.idl.api.schema.ReferenceC3Type;
import org.kinotic.idl.api.schema.ShortC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.UnionC3Type;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.api.schema.decorators.NotNullC3Decorator;
import org.kinotic.idl.internal.api.converter.DefaultIdlConverterFactory;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the pure C3 {@link FunctionDefinition} to JSON Schema transform. Schema bugs are painful to localize from
 * the higher-level e2e, so the emitter is asserted directly here with hand-built definitions.
 */
public class McpJsonSchemaGeneratorTest {

    private final McpJsonSchemaGenerator generator = new McpJsonSchemaGenerator(new DefaultIdlConverterFactory());

    @Test
    public void primitivesRequiredAndParameterDescription() {
        ParameterDefinition name = new ParameterDefinition("name", new StringC3Type());
        name.setDecorators(List.<C3Decorator>of(new NotNullC3Decorator()));
        name.setMetadata(Map.of("description", "The name to greet"));

        ParameterDefinition age = new ParameterDefinition("age", new IntC3Type());

        FunctionDefinition greet = new FunctionDefinition().setName("greet")
                                                           .addParameter(name)
                                                           .addParameter(age);

        JsonNode schema = generator.generateInputSchema(greet, Map.of());

        assertEquals("object", schema.path("type").asString());
        assertEquals("string", schema.path("properties").path("name").path("type").asString());
        assertEquals("The name to greet", schema.path("properties").path("name").path("description").asString());
        assertEquals("integer", schema.path("properties").path("age").path("type").asString());
        assertEquals("int32", schema.path("properties").path("age").path("format").asString());

        JsonNode required = schema.path("required");
        assertTrue(required.isArray());
        assertEquals(1, required.size(), "only the @NotNull parameter is required");
        assertEquals("name", required.get(0).asString());

        assertFalse(schema.has("$defs"), "no complex types means no $defs");
    }

    @Test
    public void anyEmitsUnconstrainedSchema() {
        FunctionDefinition function = new FunctionDefinition().setName("store")
                                                              .addParameter(new ParameterDefinition("value", new AnyC3Type()));

        JsonNode schema = generator.generateInputSchema(function, Map.of());

        JsonNode value = schema.path("properties").path("value");
        assertTrue(value.isObject());
        assertTrue(value.isEmpty(), "the empty schema {} accepts any value");
    }

    @Test
    public void byteAndShortEmitInteger() {
        FunctionDefinition function = new FunctionDefinition().setName("measure")
                                                             .addParameter(new ParameterDefinition("b", new ByteC3Type()))
                                                             .addParameter(new ParameterDefinition("s", new ShortC3Type()));

        JsonNode schema = generator.generateInputSchema(function, Map.of());

        JsonNode b = schema.path("properties").path("b");
        assertEquals("integer", b.path("type").asString());
        assertEquals((int) Byte.MIN_VALUE, b.path("minimum").asInt());
        assertEquals((int) Byte.MAX_VALUE, b.path("maximum").asInt());

        JsonNode s = schema.path("properties").path("s");
        assertEquals("integer", s.path("type").asString());
        assertEquals((int) Short.MIN_VALUE, s.path("minimum").asInt());
        assertEquals((int) Short.MAX_VALUE, s.path("maximum").asInt());
    }

    @Test
    public void propertyDescriptionEmitted() {
        PropertyDefinition street = new PropertyDefinition("street", new StringC3Type());
        street.setMetadata(Map.of("description", "Street line of the address"));

        ObjectC3Type address = new ObjectC3Type().setNamespace("com.acme").setName("Address")
                                                 .addProperty(street);

        FunctionDefinition function = new FunctionDefinition()
                .setName("saveAddress")
                .addParameter(new ParameterDefinition("address", new ReferenceC3Type("com.acme.Address")));

        JsonNode schema = generator.generateInputSchema(function, Map.of("com.acme.Address", address));

        assertEquals("Street line of the address",
                     schema.path("$defs").path("Address").path("properties").path("street").path("description").asString());
    }

    @Test
    public void nestingViaDefs() {
        ObjectC3Type address = new ObjectC3Type().setNamespace("com.acme").setName("Address")
                                                 .addProperty("street", new StringC3Type())
                                                 .addProperty("city", new StringC3Type());

        ObjectC3Type person = new ObjectC3Type().setNamespace("com.acme").setName("Person")
                                                .addProperty("name", new StringC3Type())
                                                .addProperty("address", new ReferenceC3Type("com.acme.Address"));

        FunctionDefinition function = new FunctionDefinition()
                .setName("createPerson")
                .addParameter(new ParameterDefinition("person", new ReferenceC3Type("com.acme.Person")));

        Map<String, ObjectC3Type> resolver = Map.of("com.acme.Person", person,
                                                    "com.acme.Address", address);

        JsonNode schema = generator.generateInputSchema(function, resolver);

        assertEquals("#/$defs/Person", schema.path("properties").path("person").path("$ref").asString());

        JsonNode defs = schema.path("$defs");
        assertTrue(defs.has("Person"));
        assertTrue(defs.has("Address"));
        assertEquals("#/$defs/Address",
                     defs.path("Person").path("properties").path("address").path("$ref").asString());
        assertEquals("string", defs.path("Address").path("properties").path("street").path("type").asString());
    }

    @Test
    public void unionToOneOfWithoutDiscriminator() {
        ObjectC3Type cat = new ObjectC3Type().setNamespace("com.acme").setName("Cat")
                                             .addProperty("meowVolume", new IntC3Type());
        ObjectC3Type dog = new ObjectC3Type().setNamespace("com.acme").setName("Dog")
                                             .addProperty("barkVolume", new IntC3Type());

        UnionC3Type pet = new UnionC3Type().setNamespace("com.acme").setName("Pet")
                                           .setTypes(List.of(cat, dog));

        FunctionDefinition function = new FunctionDefinition()
                .setName("register")
                .addParameter(new ParameterDefinition("pet", pet));

        JsonNode schema = generator.generateInputSchema(function, Map.of());

        JsonNode oneOf = schema.path("properties").path("pet").path("oneOf");
        assertTrue(oneOf.isArray());
        assertEquals(2, oneOf.size());

        Set<String> refs = new HashSet<>();
        oneOf.forEach(member -> refs.add(member.path("$ref").asString()));
        assertTrue(refs.contains("#/$defs/Cat"));
        assertTrue(refs.contains("#/$defs/Dog"));

        assertFalse(schema.toString().contains("discriminator"), "discriminator is OpenAPI vocabulary and must not be emitted");

        JsonNode defs = schema.path("$defs");
        assertEquals("integer", defs.path("Cat").path("properties").path("meowVolume").path("type").asString());
        assertTrue(defs.has("Dog"));
    }

    @Test
    public void referenceCycleTerminatesAndSelfRefs() {
        ObjectC3Type node = new ObjectC3Type().setNamespace("com.acme").setName("Node")
                                              .addProperty("value", new StringC3Type())
                                              .addProperty("children", new ArrayC3Type()
                                                      .setContains(new ReferenceC3Type("com.acme.Node")));

        FunctionDefinition function = new FunctionDefinition()
                .setName("saveTree")
                .addParameter(new ParameterDefinition("root", new ReferenceC3Type("com.acme.Node")));

        Map<String, ObjectC3Type> resolver = Map.of("com.acme.Node", node);

        JsonNode schema = generator.generateInputSchema(function, resolver);

        assertEquals("#/$defs/Node", schema.path("properties").path("root").path("$ref").asString());

        JsonNode children = schema.path("$defs").path("Node").path("properties").path("children");
        assertEquals("array", children.path("type").asString());
        assertEquals("#/$defs/Node", children.path("items").path("$ref").asString());
    }

}
