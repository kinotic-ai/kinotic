package org.kinotic.auth.compilers;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Performance benchmark comparing Cedar (in-process) vs Cerbos (gRPC sidecar)
 * for raw JSON payload authorization.
 * <p>
 * Cedar path: raw JSON array → Jackson streaming named-object → Entity.parse() → isAuthorized()
 * Cerbos path: raw JSON array → JsonFormat protobuf → gRPC checkResources()
 */
class CedarVsCerbosBenchmarkTest {

    // ========== Infrastructure ==========

    private static final CerbosContainer cerbosContainer = new CerbosContainer()
            .withClasspathResourceMapping("policies", "/policies", org.testcontainers.containers.BindMode.READ_ONLY);

    private static CerbosServiceGrpc.CerbosServiceBlockingStub cerbosStub;
    private static AuthorizationEngine cedarEngine;
    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    // ========== Cedar Policy ==========

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
    static void setup() throws Exception {
        // Cedar — in-process
        cedarEngine = new BasicAuthorizationEngine();

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

    // ========== Cedar Helpers ==========

    private static EntityUID uid(String type, String id) {
        return new EntityUID(EntityTypeName.parse(type).get(), id);
    }

    private static String rawArgsToNamedJson(String rawJsonArray, String[] paramNames) throws Exception {
        var writer = new java.io.StringWriter();
        try (var reader = MAPPER.createParser(rawJsonArray);
             var gen = MAPPER.createGenerator(writer)) {
            gen.writeStartObject();
            reader.nextToken(); // START_ARRAY
            int i = 0;
            while (reader.nextToken() != tools.jackson.core.JsonToken.END_ARRAY) {
                if (i >= paramNames.length) throw new IllegalArgumentException("More args than parameter names");
                gen.writeName(paramNames[i]);
                gen.copyCurrentStructure(reader);
                i++;
            }
            gen.writeEndObject();
        }
        return writer.toString();
    }

    private static Entity entityFromJson(String type, String id, String attrsJson) throws Exception {
        String entityJson = """
                {"uid": {"type": "%s", "id": "%s"}, "attrs": %s, "parents": []}
                """.formatted(type, id, attrsJson);
        return Entity.parse(entityJson);
    }

    private boolean cedarCheck(String rawPayload, String[] paramNames,
                               String principalAttrsJson, String action) throws Exception {
        // Step 1: Convert raw JSON array to named object
        String namedAttrs = rawArgsToNamedJson(rawPayload, paramNames);

        // Step 2: Build Cedar entities from JSON
        Entity principal = entityFromJson("User", "user-1", principalAttrsJson);
        Entity resource = entityFromJson("ServiceMethod", "req-1", namedAttrs);

        Set<Entity> entities = new HashSet<>();
        entities.add(principal);
        entities.add(resource);

        // Step 3: Evaluate
        AuthorizationRequest request = new AuthorizationRequest(
                principal.getEUID(),
                uid("Action", action),
                resource.getEUID(),
                Map.of()
        );

        PolicySet policies = PolicySet.parsePolicies(CEDAR_POLICY);
        AuthorizationResponse response = cedarEngine.isAuthorized(request, policies, entities);
        return response.success.get().isAllowed();
    }

    // ========== Cerbos Helpers ==========

    private static Value rawJsonToProtobufValue(String rawJson) throws Exception {
        String wrappedJson = "{\"args\": " + rawJson + "}";
        Struct.Builder structBuilder = Struct.newBuilder();
        JsonFormat.parser().merge(wrappedJson, structBuilder);
        return structBuilder.build().getFieldsOrThrow("args");
    }

    private boolean cerbosCheck(String rawPayload, String principalId, String[] roles,
                                Map<String, Value> principalAttrs, String action) throws Exception {
        // Step 1: Convert raw JSON to protobuf Value
        Value argsValue = rawJsonToProtobufValue(rawPayload);

        // Step 2: Build request
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

        // Step 3: gRPC call
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
    void benchmark_placeOrder_small() throws Exception {
        String rawPayload = "[{\"amount\": 25000, \"department\": \"sales\"}]";
        String[] paramNames = {"order"};
        String principalJson = "{\"roles\": [\"finance\"]}";
        int iterations = 5000;

        // Warmup
        for (int i = 0; i < 500; i++) {
            cedarCheck(rawPayload, paramNames, principalJson, "placeOrder");
            cerbosCheck(rawPayload, "user-1", new String[]{"finance"}, Map.of(), "placeOrder");
        }

        // Cedar timed run
        long cedarStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cedarCheck(rawPayload, paramNames, principalJson, "placeOrder");
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
    void benchmark_transferFunds_multiArg() throws Exception {
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
            cedarCheck(rawPayload, paramNames, principalJson, "transferFunds");
            cerbosCheck(rawPayload, "user-1", new String[]{"finance"},
                    Map.of("transferLimit", Value.newBuilder().setNumberValue(100000).build()),
                    "transferFunds");
        }

        // Cedar timed run
        long cedarStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cedarCheck(rawPayload, paramNames, principalJson, "transferFunds");
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

    @Test
    void benchmark_conversionOnly() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]""";
        String[] paramNames = {"transfer", "approval"};
        int iterations = 10000;

        // Warmup
        for (int i = 0; i < 2000; i++) {
            // Cedar conversion path
            String named = rawArgsToNamedJson(rawPayload, paramNames);
            entityFromJson("ServiceMethod", "req-1", named);
            // Cerbos conversion path
            rawJsonToProtobufValue(rawPayload);
        }

        // Cedar conversion timed run
        long cedarStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String named = rawArgsToNamedJson(rawPayload, paramNames);
            entityFromJson("ServiceMethod", "req-1", named);
        }
        long cedarElapsed = System.nanoTime() - cedarStart;

        // Cerbos conversion timed run
        long cerbosStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            rawJsonToProtobufValue(rawPayload);
        }
        long cerbosElapsed = System.nanoTime() - cerbosStart;

        double cedarMicros = (cedarElapsed / (double) iterations) / 1000.0;
        double cerbosMicros = (cerbosElapsed / (double) iterations) / 1000.0;

        System.out.printf("""
                === Conversion Only (no policy eval) ===
                Cedar  (Jackson streaming → Entity.parse): %.2f µs/call
                Cerbos (JsonFormat → protobuf Value):      %.2f µs/call
                Ratio: Cedar is %.1fx %s than Cerbos conversion
                %n""",
                cedarMicros,
                cerbosMicros,
                cedarMicros > cerbosMicros ? cedarMicros / cerbosMicros : cerbosMicros / cedarMicros,
                cedarMicros > cerbosMicros ? "slower" : "faster");
    }

    private void printResults(String label, int iterations, long cedarNanos, long cerbosNanos) {
        double cedarMicros = (cedarNanos / (double) iterations) / 1000.0;
        double cerbosMicros = (cerbosNanos / (double) iterations) / 1000.0;
        double cedarMillis = cedarMicros / 1000.0;
        double cerbosMillis = cerbosMicros / 1000.0;

        System.out.printf("""
                === %s ===
                Iterations: %,d
                Cedar  (in-process): %.2f µs (%.3f ms) — %,.0f calls/sec
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
