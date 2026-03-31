package org.kinotic.auth.compilers;

import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests calling Cedar's JNI directly with hand-built JSON, bypassing the SDK's
 * POJO serialization round-trip (JSON → Entity POJO → JSON → Rust).
 * <p>
 * The goal: raw JSON attrs go directly into the JNI request string via Jackson streaming,
 * eliminating Entity.parse() and the SDK's objectWriter().writeValueAsString() overhead.
 */
class CedarDirectJniTest {

    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    // Reflection handles for Cedar JNI
    private static MethodHandle callCedarJNI;
    private static MethodHandle getCedarJNIVersion;

    // Cached policy JSON (serialized once via SDK's ObjectMapper)
    private static String cachedPolicyJson;

    private static final String CEDAR_POLICY = """
            permit(
                principal,
                action == Action::"placeOrder",
                resource is ServiceMethod
            ) when {
                principal.roles.contains("finance") &&
                resource.order.amount < 50000
            };

            permit(
                principal,
                action == Action::"transferFunds",
                resource is ServiceMethod
            ) when {
                principal.roles.contains("finance") &&
                resource.transfer.amount < principal.transferLimit &&
                resource.transfer.currency == "USD" &&
                resource.approval.approved
            };
            """;

    @BeforeAll
    static void setup() throws Throwable {
        // Force native library load by instantiating the engine
        new BasicAuthorizationEngine();

        // Get reflection access to the private native methods
        Method callMethod = BasicAuthorizationEngine.class.getDeclaredMethod("callCedarJNI", String.class, String.class);
        callMethod.setAccessible(true);
        callCedarJNI = MethodHandles.lookup().unreflect(callMethod);

        Method versionMethod = BasicAuthorizationEngine.class.getDeclaredMethod("getCedarJNIVersion");
        versionMethod.setAccessible(true);
        getCedarJNIVersion = MethodHandles.lookup().unreflect(versionMethod);

        // Step 1: Capture the exact JSON format by running one SDK request and intercepting
        // We'll use the SDK's own ObjectMapper to serialize a PolicySet
        // Access CedarJson.objectWriter() via reflection
        Class<?> cedarJsonClass = Class.forName("com.cedarpolicy.CedarJson");
        Method objectWriterMethod = cedarJsonClass.getDeclaredMethod("objectWriter");
        objectWriterMethod.setAccessible(true);
        com.fasterxml.jackson.databind.ObjectWriter cedarWriter =
                (com.fasterxml.jackson.databind.ObjectWriter) objectWriterMethod.invoke(null);

        // Serialize a known request to capture the format
        PolicySet policies = PolicySet.parsePolicies(CEDAR_POLICY);

        // Build a minimal request using the SDK to see the JSON format
        Entity principal = Entity.parse("""
                {"uid": {"type": "User", "id": "test"}, "attrs": {}, "parents": []}""");
        Entity resource = Entity.parse("""
                {"uid": {"type": "ServiceMethod", "id": "test"}, "attrs": {}, "parents": []}""");
        Set<Entity> entities = Set.of(principal, resource);

        EntityUID principalUid = principal.getEUID();
        EntityUID actionUid = new EntityUID(EntityTypeName.parse("Action").get(), "test");
        EntityUID resourceUid = resource.getEUID();

        AuthorizationRequest pubRequest = new AuthorizationRequest(principalUid, actionUid, resourceUid, Map.of());

        // Create the inner AuthorizationRequest that BasicAuthorizationEngine normally creates
        // It's a private inner class, so we need to use the cedarWriter on a wrapper
        // Instead, let's just serialize the PolicySet and entities separately
        cachedPolicyJson = cedarWriter.writeValueAsString(policies);

        System.out.println("=== Captured PolicySet JSON ===");
        System.out.println(cachedPolicyJson);

        // Also capture entity format
        String entityJson = cedarWriter.writeValueAsString(principal);
        System.out.println("\n=== Captured Entity JSON ===");
        System.out.println(entityJson);

        // Capture full request format by building the inner class via reflection
        // The inner AuthorizationRequest extends the public one and adds policies + entities
        Class<?>[] innerClasses = BasicAuthorizationEngine.class.getDeclaredClasses();
        Class<?> innerRequestClass = null;
        for (Class<?> c : innerClasses) {
            if (c.getSimpleName().equals("AuthorizationRequest")) {
                innerRequestClass = c;
                break;
            }
        }
        assertNotNull(innerRequestClass, "Could not find inner AuthorizationRequest class");

        // Create instance via constructor
        var constructor = innerRequestClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object innerRequest = constructor.newInstance(pubRequest, policies, entities);

        String fullRequestJson = cedarWriter.writeValueAsString(innerRequest);
        System.out.println("\n=== Full Authorization Request JSON ===");
        System.out.println(fullRequestJson);

        // Now call JNI with this JSON to verify it works
        String version = (String) getCedarJNIVersion.invoke();
        System.out.println("\n=== Cedar JNI Version: " + version + " ===");

        String responseJson = (String) callCedarJNI.invoke(
                "AuthorizationOperation", fullRequestJson);
        System.out.println("\n=== Response JSON ===");
        System.out.println(responseJson);
    }

    // ========== Helpers ==========

    /**
     * Converts raw JSON array to named object using Jackson streaming.
     * [{"amount": 25000}] with names ["order"] → {"order": {"amount": 25000}}
     */
    private static String rawArgsToNamedJson(String rawJsonArray, String[] paramNames) throws Exception {
        var writer = new java.io.StringWriter();
        try (var reader = MAPPER.createParser(rawJsonArray);
             var gen = MAPPER.createGenerator(writer)) {
            gen.writeStartObject();
            reader.nextToken(); // START_ARRAY
            int i = 0;
            while (reader.nextToken() != tools.jackson.core.JsonToken.END_ARRAY) {
                gen.writeName(paramNames[i]);
                gen.copyCurrentStructure(reader);
                i++;
            }
            gen.writeEndObject();
        }
        return writer.toString();
    }

    /**
     * Builds the full Cedar JNI request JSON using streaming, embedding raw attrs
     * directly without Entity.parse() or SDK serialization.
     */
    private static String buildRequestJson(
            String principalType, String principalId, String principalAttrsJson,
            String actionType, String actionId,
            String resourceType, String resourceId, String resourceAttrsJson,
            String policiesJson) throws Exception {

        var writer = new java.io.StringWriter(1024);
        try (var gen = MAPPER.createGenerator(writer)) {
            gen.writeStartObject();

            // principal EUID — uses __entity wrapper
            gen.writeName("principal");
            writeEuidJson(gen, principalType, principalId);

            // action EUID
            gen.writeName("action");
            writeEuidJson(gen, actionType, actionId);

            // resource EUID
            gen.writeName("resource");
            writeEuidJson(gen, resourceType, resourceId);

            // context (empty)
            gen.writeName("context");
            gen.writeStartObject();
            gen.writeEndObject();

            // policies — pre-serialized, embed as raw JSON
            gen.writeName("policies");
            gen.writeRawValue(policiesJson);

            // entities array — build inline with raw attrs
            gen.writeName("entities");
            gen.writeStartArray();

            // Principal entity
            writeEntityJson(gen, principalType, principalId, principalAttrsJson);

            // Resource entity
            writeEntityJson(gen, resourceType, resourceId, resourceAttrsJson);

            gen.writeEndArray();

            gen.writeEndObject();
        }
        return writer.toString();
    }

    /**
     * Writes an EntityUID in Cedar's __entity wrapper format:
     * {"__entity": {"type": "User", "id": "user-1"}}
     */
    private static void writeEuidJson(tools.jackson.core.JsonGenerator gen,
                                       String type, String id) throws Exception {
        gen.writeStartObject();
        gen.writeName("__entity");
        gen.writeStartObject();
        gen.writeName("type");
        gen.writeString(type);
        gen.writeName("id");
        gen.writeString(id);
        gen.writeEndObject();
        gen.writeEndObject();
    }

    /**
     * Writes a single Cedar entity JSON object, embedding raw attrs JSON directly.
     */
    private static void writeEntityJson(tools.jackson.core.JsonGenerator gen,
                                         String type, String id, String attrsJson) throws Exception {
        gen.writeStartObject();

        // uid
        gen.writeName("uid");
        gen.writeStartObject();
        gen.writeName("type");
        gen.writeString(type);
        gen.writeName("id");
        gen.writeString(id);
        gen.writeEndObject();

        // attrs — raw JSON embedded directly
        gen.writeName("attrs");
        gen.writeRawValue(attrsJson);

        // parents — empty array
        gen.writeName("parents");
        gen.writeStartArray();
        gen.writeEndArray();

        // tags — empty object
        gen.writeName("tags");
        gen.writeStartObject();
        gen.writeEndObject();

        gen.writeEndObject();
    }

    /**
     * Calls Cedar JNI directly with hand-built JSON. Returns true if allowed.
     */
    private static boolean directCedarCheck(
            String principalAttrsJson, String action,
            String resourceAttrsJson) throws Throwable {

        String requestJson = buildRequestJson(
                "User", "user-1", principalAttrsJson,
                "Action", action,
                "ServiceMethod", "req-1", resourceAttrsJson,
                cachedPolicyJson);

        String responseJson = (String) callCedarJNI.invoke("AuthorizationOperation", requestJson);

        // Fast check — decision is lowercase in JNI response
        return responseJson.contains("\"allow\"");
    }

    /**
     * Full flow: raw JSON array → named args → direct JNI call.
     */
    private static boolean directCedarCheckFromRaw(
            String rawPayload, String[] paramNames,
            String principalAttrsJson, String action) throws Throwable {

        String namedAttrs = rawArgsToNamedJson(rawPayload, paramNames);
        return directCedarCheck(principalAttrsJson, action, namedAttrs);
    }

    // ========== Correctness Tests ==========

    @Test
    void directJni_placeOrder_allowed() throws Throwable {
        assertTrue(directCedarCheckFromRaw(
                "[{\"amount\": 25000, \"department\": \"sales\"}]",
                new String[]{"order"},
                "{\"roles\": [\"finance\"]}",
                "placeOrder"));
    }

    @Test
    void directJni_placeOrder_denied_overLimit() throws Throwable {
        assertFalse(directCedarCheckFromRaw(
                "[{\"amount\": 75000, \"department\": \"sales\"}]",
                new String[]{"order"},
                "{\"roles\": [\"finance\"]}",
                "placeOrder"));
    }

    @Test
    void directJni_transferFunds_allowed() throws Throwable {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]""";
        assertTrue(directCedarCheckFromRaw(rawPayload,
                new String[]{"transfer", "approval"},
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds"));
    }

    @Test
    void directJni_transferFunds_denied_wrongCurrency() throws Throwable {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "EUR"},
                    {"approved": true, "approver": "manager-1"}
                ]""";
        assertFalse(directCedarCheckFromRaw(rawPayload,
                new String[]{"transfer", "approval"},
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds"));
    }

    // ========== Performance Benchmark: SDK vs Direct JNI ==========

    @Test
    void benchmark_sdkVsDirectJni() throws Throwable {
        String rawPayload = "[{\"amount\": 25000, \"department\": \"sales\"}]";
        String[] paramNames = {"order"};
        String principalJson = "{\"roles\": [\"finance\"]}";
        int iterations = 10000;

        // SDK setup
        var engine = new BasicAuthorizationEngine();
        PolicySet policies = PolicySet.parsePolicies(CEDAR_POLICY);

        // Warmup — SDK path
        for (int i = 0; i < 1000; i++) {
            String named = rawArgsToNamedJson(rawPayload, paramNames);
            Entity principal = Entity.parse(
                    "{\"uid\": {\"type\": \"User\", \"id\": \"u1\"}, \"attrs\": " + principalJson + ", \"parents\": []}");
            Entity resource = Entity.parse(
                    "{\"uid\": {\"type\": \"ServiceMethod\", \"id\": \"r1\"}, \"attrs\": " + named + ", \"parents\": []}");
            AuthorizationRequest req = new AuthorizationRequest(
                    principal.getEUID(),
                    new EntityUID(EntityTypeName.parse("Action").get(), "placeOrder"),
                    resource.getEUID(),
                    Map.of());
            engine.isAuthorized(req, policies, Set.of(principal, resource));
        }

        // Warmup — Direct JNI path
        for (int i = 0; i < 1000; i++) {
            directCedarCheckFromRaw(rawPayload, paramNames, principalJson, "placeOrder");
        }

        // Timed — SDK path
        long sdkStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String named = rawArgsToNamedJson(rawPayload, paramNames);
            Entity principal = Entity.parse(
                    "{\"uid\": {\"type\": \"User\", \"id\": \"u1\"}, \"attrs\": " + principalJson + ", \"parents\": []}");
            Entity resource = Entity.parse(
                    "{\"uid\": {\"type\": \"ServiceMethod\", \"id\": \"r1\"}, \"attrs\": " + named + ", \"parents\": []}");
            AuthorizationRequest req = new AuthorizationRequest(
                    principal.getEUID(),
                    new EntityUID(EntityTypeName.parse("Action").get(), "placeOrder"),
                    resource.getEUID(),
                    Map.of());
            engine.isAuthorized(req, policies, Set.of(principal, resource));
        }
        long sdkElapsed = System.nanoTime() - sdkStart;

        // Timed — Direct JNI path
        long directStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            directCedarCheckFromRaw(rawPayload, paramNames, principalJson, "placeOrder");
        }
        long directElapsed = System.nanoTime() - directStart;

        double sdkMicros = (sdkElapsed / (double) iterations) / 1000.0;
        double directMicros = (directElapsed / (double) iterations) / 1000.0;

        System.out.printf("""

                === Cedar SDK vs Direct JNI (placeOrder, small payload) ===
                Iterations: %,d
                SDK    (Entity.parse → isAuthorized):  %.2f µs — %,.0f calls/sec
                Direct (streaming JSON → callCedarJNI): %.2f µs — %,.0f calls/sec
                Speedup: %.1fx faster with direct JNI
                %n""",
                iterations,
                sdkMicros, 1_000_000.0 / sdkMicros,
                directMicros, 1_000_000.0 / directMicros,
                sdkMicros / directMicros);
    }

    @Test
    void benchmark_sdkVsDirectJni_multiArg() throws Throwable {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]""";
        String[] paramNames = {"transfer", "approval"};
        String principalJson = "{\"roles\": [\"finance\"], \"transferLimit\": 100000}";
        int iterations = 10000;

        // SDK setup
        var engine = new BasicAuthorizationEngine();
        PolicySet policies = PolicySet.parsePolicies(CEDAR_POLICY);

        // Warmup
        for (int i = 0; i < 1000; i++) {
            String named = rawArgsToNamedJson(rawPayload, paramNames);
            Entity principal = Entity.parse(
                    "{\"uid\": {\"type\": \"User\", \"id\": \"u1\"}, \"attrs\": " + principalJson + ", \"parents\": []}");
            Entity resource = Entity.parse(
                    "{\"uid\": {\"type\": \"ServiceMethod\", \"id\": \"r1\"}, \"attrs\": " + named + ", \"parents\": []}");
            AuthorizationRequest req = new AuthorizationRequest(
                    principal.getEUID(),
                    new EntityUID(EntityTypeName.parse("Action").get(), "transferFunds"),
                    resource.getEUID(),
                    Map.of());
            engine.isAuthorized(req, policies, Set.of(principal, resource));

            directCedarCheckFromRaw(rawPayload, paramNames, principalJson, "transferFunds");
        }

        // Timed — SDK
        long sdkStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String named = rawArgsToNamedJson(rawPayload, paramNames);
            Entity principal = Entity.parse(
                    "{\"uid\": {\"type\": \"User\", \"id\": \"u1\"}, \"attrs\": " + principalJson + ", \"parents\": []}");
            Entity resource = Entity.parse(
                    "{\"uid\": {\"type\": \"ServiceMethod\", \"id\": \"r1\"}, \"attrs\": " + named + ", \"parents\": []}");
            AuthorizationRequest req = new AuthorizationRequest(
                    principal.getEUID(),
                    new EntityUID(EntityTypeName.parse("Action").get(), "transferFunds"),
                    resource.getEUID(),
                    Map.of());
            engine.isAuthorized(req, policies, Set.of(principal, resource));
        }
        long sdkElapsed = System.nanoTime() - sdkStart;

        // Timed — Direct
        long directStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            directCedarCheckFromRaw(rawPayload, paramNames, principalJson, "transferFunds");
        }
        long directElapsed = System.nanoTime() - directStart;

        double sdkMicros = (sdkElapsed / (double) iterations) / 1000.0;
        double directMicros = (directElapsed / (double) iterations) / 1000.0;

        System.out.printf("""

                === Cedar SDK vs Direct JNI (transferFunds, multi-arg) ===
                Iterations: %,d
                SDK    (Entity.parse → isAuthorized):  %.2f µs — %,.0f calls/sec
                Direct (streaming JSON → callCedarJNI): %.2f µs — %,.0f calls/sec
                Speedup: %.1fx faster with direct JNI
                %n""",
                iterations,
                sdkMicros, 1_000_000.0 / sdkMicros,
                directMicros, 1_000_000.0 / directMicros,
                sdkMicros / directMicros);
    }
}
