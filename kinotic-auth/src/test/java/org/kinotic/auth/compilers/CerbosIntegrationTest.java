package org.kinotic.auth.compilers;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.cerbos.api.v1.engine.Engine;
import dev.cerbos.api.v1.request.Request;
import dev.cerbos.api.v1.response.Response;
import dev.cerbos.api.v1.svc.CerbosServiceGrpc;
import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.PlanResourcesResult;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import dev.cerbos.sdk.containers.CerbosContainer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static dev.cerbos.sdk.builders.AttributeValue.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify Cerbos can evaluate policies against raw JSON payloads.
 * <p>
 * The key requirement: the gateway receives a raw JSON byte array like
 * {@code [{"amount": 25000, "department": "sales"}, {"approved": true}]}
 * and passes it to Cerbos as {@code R.attr.args} without field-by-field conversion.
 * CEL conditions then access it via {@code R.attr.args[0].amount}.
 */
class CerbosIntegrationTest {

    private static final CerbosContainer cerbosContainer = new CerbosContainer()
            .withClasspathResourceMapping("policies", "/policies", org.testcontainers.containers.BindMode.READ_ONLY);

    private static CerbosBlockingClient client;
    private static CerbosServiceGrpc.CerbosServiceBlockingStub rawStub;

    @BeforeAll
    static void setup() throws Exception {
        cerbosContainer.start();
        client = new CerbosClientBuilder(cerbosContainer.getTarget())
                .withPlaintext()
                .buildBlockingClient();

        // Also create a raw gRPC stub for tests that need to pass raw protobuf values
        String target = cerbosContainer.getTarget();
        String[] parts = target.split(":");
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(parts[0], Integer.parseInt(parts[1]))
                .usePlaintext()
                .build();
        rawStub = CerbosServiceGrpc.newBlockingStub(channel);
    }

    // ========== Helper: Convert raw JSON args to protobuf Value ==========

    /**
     * Simulates what the gateway would do: take the raw JSON payload bytes
     * and convert them to a protobuf Value in a single parse call.
     * This is the ONLY parse step — no field-by-field construction.
     */
    private static Value rawJsonToProtobufValue(String rawJson) throws Exception {
        String wrappedJson = "{\"args\": " + rawJson + "}";
        Struct.Builder structBuilder = Struct.newBuilder();
        JsonFormat.parser().merge(wrappedJson, structBuilder);
        return structBuilder.build().getFieldsOrThrow("args");
    }

    /**
     * Checks a raw JSON args payload against Cerbos using the protobuf API directly,
     * bypassing the builder DSL that requires AttributeValue construction.
     */
    private static Response.CheckResourcesResponse checkWithRawJson(
            Engine.Principal principal,
            String resourceType,
            String resourceId,
            String rawJsonArgs,
            String... actions) throws Exception {

        Value argsValue = rawJsonToProtobufValue(rawJsonArgs);

        Engine.Resource.Builder resourceBuilder = Engine.Resource.newBuilder()
                .setKind(resourceType)
                .setId(resourceId)
                .putAttr("args", argsValue);

        Request.CheckResourcesRequest.ResourceEntry.Builder entryBuilder =
                Request.CheckResourcesRequest.ResourceEntry.newBuilder()
                        .setResource(resourceBuilder);
        for (String action : actions) {
            entryBuilder.addActions(action);
        }

        Request.CheckResourcesRequest request = Request.CheckResourcesRequest.newBuilder()
                .setRequestId("test-" + System.nanoTime())
                .setPrincipal(principal)
                .addResources(entryBuilder)
                .build();

        return rawStub.checkResources(request);
    }

    private static boolean isAllowed(Response.CheckResourcesResponse response, String action) {
        return response.getResultsList().stream()
                       .flatMap(r -> r.getActionsMap().entrySet().stream())
                       .filter(e -> e.getKey().equals(action))
                       .anyMatch(e -> e.getValue().getEffect() == dev.cerbos.api.v1.effect.Effect.EFFECT_ALLOW);
    }

    // ========== Raw JSON Payload Tests ==========

    @Test
    void rawJsonSingleArg_placeOrderUnderLimit() throws Exception {
        String rawPayload = "[{\"amount\": 25000, \"department\": \"sales\"}]";

        Engine.Principal principal = Engine.Principal.newBuilder()
                .setId("user-1")
                .addRoles("finance")
                .build();

        Response.CheckResourcesResponse response = checkWithRawJson(
                principal, "service_method", "req-1", rawPayload, "placeOrder");

        assertTrue(isAllowed(response, "placeOrder"));
    }

    @Test
    void rawJsonSingleArg_placeOrderOverLimit() throws Exception {
        String rawPayload = "[{\"amount\": 75000, \"department\": \"sales\"}]";

        Engine.Principal principal = Engine.Principal.newBuilder()
                .setId("user-1")
                .addRoles("finance")
                .build();

        Response.CheckResourcesResponse response = checkWithRawJson(
                principal, "service_method", "req-1", rawPayload, "placeOrder");

        assertFalse(isAllowed(response, "placeOrder"));
    }

    @Test
    void rawJsonMultipleArgs_transferApproved() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]
                """;

        Engine.Principal principal = Engine.Principal.newBuilder()
                .setId("user-1")
                .addRoles("finance")
                .putAttr("transferLimit", Value.newBuilder().setNumberValue(100000).build())
                .build();

        Response.CheckResourcesResponse response = checkWithRawJson(
                principal, "service_method", "req-1", rawPayload, "transferFunds");

        assertTrue(isAllowed(response, "transferFunds"));
    }

    @Test
    void rawJsonMultipleArgs_transferNotApproved() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": false, "approver": "manager-1"}
                ]
                """;

        Engine.Principal principal = Engine.Principal.newBuilder()
                .setId("user-1")
                .addRoles("finance")
                .putAttr("transferLimit", Value.newBuilder().setNumberValue(100000).build())
                .build();

        Response.CheckResourcesResponse response = checkWithRawJson(
                principal, "service_method", "req-1", rawPayload, "transferFunds");

        assertFalse(isAllowed(response, "transferFunds"));
    }

    @Test
    void rawJsonMultipleArgs_transferWrongCurrency() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "EUR"},
                    {"approved": true, "approver": "manager-1"}
                ]
                """;

        Engine.Principal principal = Engine.Principal.newBuilder()
                .setId("user-1")
                .addRoles("finance")
                .putAttr("transferLimit", Value.newBuilder().setNumberValue(100000).build())
                .build();

        Response.CheckResourcesResponse response = checkWithRawJson(
                principal, "service_method", "req-1", rawPayload, "transferFunds");

        assertFalse(isAllowed(response, "transferFunds"));
    }

    @Test
    void rawJsonNestedObjects() throws Exception {
        String rawPayload = """
                [{"address": {"city": "Austin", "state": "TX"}, "amount": 1000}]
                """;

        Engine.Principal principal = Engine.Principal.newBuilder()
                .setId("user-1")
                .addRoles("finance")
                .build();

        Response.CheckResourcesResponse response = checkWithRawJson(
                principal, "service_method", "req-1", rawPayload, "placeOrder");

        // amount is 1000, under the 50000 limit
        assertTrue(isAllowed(response, "placeOrder"));
    }

    // ========== Simple RBAC (no payload needed) ==========

    @Test
    void adminRoleAllowsAllActions() {
        CheckResult result = client.check(
                Principal.newInstance("admin-user", "admin"),
                Resource.newInstance("entity", "entity-1"),
                "read", "create", "delete"
        );

        assertTrue(result.isAllowed("read"));
        assertTrue(result.isAllowed("create"));
        assertTrue(result.isAllowed("delete"));
    }

    // ========== ABAC with Entity Attributes ==========

    @Test
    void userCanReadEntityInSameDepartment() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "user")
                         .withAttribute("department", stringValue("engineering")),
                Resource.newInstance("entity", "entity-1")
                        .withAttribute("department", stringValue("engineering")),
                "read"
        );

        assertTrue(result.isAllowed("read"));
    }

    @Test
    void userCannotReadEntityInDifferentDepartment() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "user")
                         .withAttribute("department", stringValue("engineering")),
                Resource.newInstance("entity", "entity-1")
                        .withAttribute("department", stringValue("finance"))
                        .withAttribute("status", stringValue("archived")),
                "read"
        );

        assertFalse(result.isAllowed("read"));
    }

    // ========== PlanResources (Query Filter Generation) ==========

    @Test
    void planResourcesForAdminReturnsAlwaysAllowed() {
        PlanResourcesResult plan = client.plan(
                Principal.newInstance("admin-user", "admin"),
                Resource.newInstance("entity"),
                "read"
        );

        assertTrue(plan.isAlwaysAllowed());
    }

    @Test
    void planResourcesForUserReturnsCondition() {
        PlanResourcesResult plan = client.plan(
                Principal.newInstance("user-1", "user")
                         .withAttribute("department", stringValue("engineering")),
                Resource.newInstance("entity"),
                "read"
        );

        assertFalse(plan.isAlwaysAllowed());
        assertFalse(plan.isAlwaysDenied());
        assertNotNull(plan.getCondition());
    }
}
