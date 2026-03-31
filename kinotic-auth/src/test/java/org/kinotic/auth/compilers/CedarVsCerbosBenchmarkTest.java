package org.kinotic.auth.compilers;

import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.policy.PolicySet;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.cerbos.api.v1.engine.Engine;
import dev.cerbos.api.v1.request.Request;
import dev.cerbos.api.v1.response.Response;
import dev.cerbos.api.v1.svc.CerbosServiceGrpc;
import dev.cerbos.sdk.CerbosContainer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Performance benchmark comparing Cedar Direct JNI (in-process) vs Cerbos (gRPC sidecar)
 * for raw JSON payload authorization.
 * <p>
 * Cedar path: single-pass Jackson streaming → callCedarJNI (no POJO round-trip)
 * Cerbos path: raw JSON → JsonFormat protobuf → gRPC checkResources()
 */
class CedarVsCerbosBenchmarkTest {

    // ========== Infrastructure ==========

    private static final CerbosContainer cerbosContainer = new CerbosContainer()
            .withClasspathResourceMapping("policies", "/policies", org.testcontainers.containers.BindMode.READ_ONLY);

    private static CerbosServiceGrpc.CerbosServiceBlockingStub cerbosStub;
    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    // Cedar Direct JNI
    private static MethodHandle callCedarJNI;
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
        // Cedar — direct JNI setup
        new BasicAuthorizationEngine(); // loads native library

        Method callMethod = BasicAuthorizationEngine.class.getDeclaredMethod("callCedarJNI", String.class, String.class);
        callMethod.setAccessible(true);
        callCedarJNI = MethodHandles.lookup().unreflect(callMethod);

        // Cache policy JSON via SDK's ObjectMapper (done once)
        Class<?> cedarJsonClass = Class.forName("com.cedarpolicy.CedarJson");
        Method objectWriterMethod = cedarJsonClass.getDeclaredMethod("objectWriter");
        objectWriterMethod.setAccessible(true);
        com.fasterxml.jackson.databind.ObjectWriter cedarWriter =
                (com.fasterxml.jackson.databind.ObjectWriter) objectWriterMethod.invoke(null);
        PolicySet policies = PolicySet.parsePolicies(CEDAR_POLICY);
        cachedPolicyJson = cedarWriter.writeValueAsString(policies);

        // Cerbos — Docker container
        cerbosContainer.start();
        String target = cerbosContainer.getTarget();
        String[] parts = target.split(":");
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(parts[0], Integer.parseInt(parts[1]))
                .usePlaintext()
                .build();
        cerbosStub = CerbosServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void teardown() {
        cerbosContainer.stop();
    }

    // ========== Cedar Direct JNI — single pass ==========

    /**
     * Builds the entire Cedar JNI request JSON in a single streaming pass,
     * transforming raw JSON array → named object inline. No intermediate strings.
     */
    private static boolean cedarDirectCheck(String rawPayload, String[] paramNames,
                                             String principalAttrsJson, String action) throws Throwable {
        var writer = new java.io.StringWriter(512);
        try (var gen = MAPPER.createGenerator(writer)) {
            gen.writeStartObject();

            // principal EUID
            gen.writeName("principal");
            writeEuidJson(gen, "User", "user-1");

            // action EUID
            gen.writeName("action");
            writeEuidJson(gen, "Action", action);

            // resource EUID
            gen.writeName("resource");
            writeEuidJson(gen, "ServiceMethod", "req-1");

            // context
            gen.writeName("context");
            gen.writeStartObject();
            gen.writeEndObject();

            // policies — cached
            gen.writeName("policies");
            gen.writeRawValue(cachedPolicyJson);

            // entities
            gen.writeName("entities");
            gen.writeStartArray();

            // Principal entity
            gen.writeStartObject();
            gen.writeName("uid");
            gen.writeStartObject();
            gen.writeName("type");
            gen.writeString("User");
            gen.writeName("id");
            gen.writeString("user-1");
            gen.writeEndObject();
            gen.writeName("attrs");
            gen.writeRawValue(principalAttrsJson);
            gen.writeName("parents");
            gen.writeStartArray();
            gen.writeEndArray();
            gen.writeName("tags");
            gen.writeStartObject();
            gen.writeEndObject();
            gen.writeEndObject();

            // Resource entity — fused: parse raw array inline as named object
            gen.writeStartObject();
            gen.writeName("uid");
            gen.writeStartObject();
            gen.writeName("type");
            gen.writeString("ServiceMethod");
            gen.writeName("id");
            gen.writeString("req-1");
            gen.writeEndObject();
            gen.writeName("attrs");
            gen.writeStartObject();
            try (var argParser = MAPPER.createParser(rawPayload)) {
                argParser.nextToken(); // START_ARRAY
                int i = 0;
                while (argParser.nextToken() != tools.jackson.core.JsonToken.END_ARRAY) {
                    gen.writeName(paramNames[i]);
                    gen.copyCurrentStructure(argParser);
                    i++;
                }
            }
            gen.writeEndObject();
            gen.writeName("parents");
            gen.writeStartArray();
            gen.writeEndArray();
            gen.writeName("tags");
            gen.writeStartObject();
            gen.writeEndObject();
            gen.writeEndObject();

            gen.writeEndArray();
            gen.writeEndObject();
        }

        String responseJson = (String) callCedarJNI.invoke("AuthorizationOperation", writer.toString());
        return responseJson.contains("\"allow\"");
    }

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

    // ========== Cerbos ==========

    private static Value rawJsonToProtobufValue(String rawJson) throws Exception {
        String wrappedJson = "{\"args\": " + rawJson + "}";
        Struct.Builder structBuilder = Struct.newBuilder();
        JsonFormat.parser().merge(wrappedJson, structBuilder);
        return structBuilder.build().getFieldsOrThrow("args");
    }

    private boolean cerbosCheck(String rawPayload, String principalId, String[] roles,
                                Map<String, Value> principalAttrs, String action) throws Exception {
        Value argsValue = rawJsonToProtobufValue(rawPayload);

        Engine.Principal.Builder principalBuilder = Engine.Principal.newBuilder()
                .setId(principalId);
        for (String role : roles) {
            principalBuilder.addRoles(role);
        }
        for (var entry : principalAttrs.entrySet()) {
            principalBuilder.putAttr(entry.getKey(), entry.getValue());
        }

        Engine.Resource.Builder resourceBuilder = Engine.Resource.newBuilder()
                .setKind("service_method")
                .setId("req-1")
                .putAttr("args", argsValue);

        Request.CheckResourcesRequest.ResourceEntry.Builder entryBuilder =
                Request.CheckResourcesRequest.ResourceEntry.newBuilder()
                        .setResource(resourceBuilder)
                        .addActions(action);

        Request.CheckResourcesRequest request = Request.CheckResourcesRequest.newBuilder()
                .setRequestId("bench-" + System.nanoTime())
                .setPrincipal(principalBuilder)
                .addResources(entryBuilder)
                .build();

        Response.CheckResourcesResponse response = cerbosStub.checkResources(request);
        for (Response.CheckResourcesResponse.ResultEntry result : response.getResultsList()) {
            for (var entry : result.getActionsMap().entrySet()) {
                if (entry.getKey().equals(action)) {
                    return entry.getValue().getNumber() == 1;
                }
            }
        }
        return false;
    }

    // ========== Benchmarks ==========

    @Test
    void benchmark_placeOrder_small() throws Throwable {
        String rawPayload = "[{\"amount\": 25000, \"department\": \"sales\"}]";
        String[] paramNames = {"order"};
        String principalJson = "{\"roles\": [\"finance\"]}";
        int iterations = 5000;

        // Warmup
        for (int i = 0; i < 500; i++) {
            cedarDirectCheck(rawPayload, paramNames, principalJson, "placeOrder");
            cerbosCheck(rawPayload, "user-1", new String[]{"finance"}, Map.of(), "placeOrder");
        }

        // Cedar Direct JNI timed run
        long cedarStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cedarDirectCheck(rawPayload, paramNames, principalJson, "placeOrder");
        }
        long cedarElapsed = System.nanoTime() - cedarStart;

        // Cerbos timed run
        long cerbosStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cerbosCheck(rawPayload, "user-1", new String[]{"finance"}, Map.of(), "placeOrder");
        }
        long cerbosElapsed = System.nanoTime() - cerbosStart;

        printResults("placeOrder (small, 1 arg)", iterations, cedarElapsed, cerbosElapsed);
    }

    @Test
    void benchmark_transferFunds_multiArg() throws Throwable {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]""";
        String[] paramNames = {"transfer", "approval"};
        String principalJson = "{\"roles\": [\"finance\"], \"transferLimit\": 100000}";
        int iterations = 5000;

        // Warmup
        for (int i = 0; i < 500; i++) {
            cedarDirectCheck(rawPayload, paramNames, principalJson, "transferFunds");
            cerbosCheck(rawPayload, "user-1", new String[]{"finance"},
                    Map.of("transferLimit", Value.newBuilder().setNumberValue(100000).build()),
                    "transferFunds");
        }

        // Cedar Direct JNI timed run
        long cedarStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cedarDirectCheck(rawPayload, paramNames, principalJson, "transferFunds");
        }
        long cedarElapsed = System.nanoTime() - cedarStart;

        // Cerbos timed run
        long cerbosStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cerbosCheck(rawPayload, "user-1", new String[]{"finance"},
                    Map.of("transferLimit", Value.newBuilder().setNumberValue(100000).build()),
                    "transferFunds");
        }
        long cerbosElapsed = System.nanoTime() - cerbosStart;

        printResults("transferFunds (multi-arg)", iterations, cedarElapsed, cerbosElapsed);
    }

    private void printResults(String label, int iterations, long cedarNanos, long cerbosNanos) {
        double cedarMicros = (cedarNanos / (double) iterations) / 1000.0;
        double cerbosMicros = (cerbosNanos / (double) iterations) / 1000.0;
        double cedarMillis = cedarMicros / 1000.0;
        double cerbosMillis = cerbosMicros / 1000.0;

        System.out.printf("""
                === %s ===
                Iterations: %,d
                Cedar  (direct JNI): %.2f µs (%.3f ms) — %,.0f calls/sec
                Cerbos (gRPC):       %.2f µs (%.3f ms) — %,.0f calls/sec
                Ratio: Cedar is %.1fx %s than Cerbos
                %n""",
                label,
                iterations,
                cedarMicros, cedarMillis, 1_000_000.0 / cedarMicros,
                cerbosMicros, cerbosMillis, 1_000_000.0 / cerbosMicros,
                cedarMicros > cerbosMicros ? cedarMicros / cerbosMicros : cerbosMicros / cedarMicros,
                cedarMicros > cerbosMicros ? "slower" : "faster");
    }
}
