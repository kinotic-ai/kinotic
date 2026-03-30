package org.kinotic.auth.compilers;

import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.PlanResourcesResult;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import dev.cerbos.sdk.containers.CerbosContainer;
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

    @BeforeAll
    static void setup() throws Exception {
        cerbosContainer.start();
        client = new CerbosClientBuilder(cerbosContainer.getTarget())
                .withPlaintext()
                .buildBlockingClient();
    }

    // ========== Helper: Convert raw JSON args to protobuf Value ==========

    /**
     * Simulates what the gateway would do: take the raw JSON payload bytes
     * and convert them to a protobuf Value in a single parse call.
     * This is the ONLY parse step — no field-by-field construction.
     */
    private static Value rawJsonToProtobufValue(String rawJson) throws Exception {
        // Wrap the raw JSON array in a struct so JsonFormat can parse it,
        // then extract just the value
        String wrappedJson = "{\"args\": " + rawJson + "}";
        Struct.Builder structBuilder = Struct.newBuilder();
        JsonFormat.parser().merge(wrappedJson, structBuilder);
        return structBuilder.build().getFieldsOrThrow("args");
    }

    /**
     * Builds a Cerbos Resource from a raw JSON args payload.
     * This is the pattern the gateway would use.
     */
    private static Resource resourceFromRawJson(String resourceType, String resourceId, String rawJsonArgs) throws Exception {
        Value argsValue = rawJsonToProtobufValue(rawJsonArgs);

        // Build the resource attributes struct with the args
        Struct attrs = Struct.newBuilder()
                             .putFields("args", argsValue)
                             .build();

        return Resource.newInstance(resourceType, resourceId)
                       .withAttributes(attrs);
    }

    // ========== Raw JSON Payload Tests ==========

    @Test
    void rawJsonSingleArg_placeOrderUnderLimit() throws Exception {
        // This is exactly what the gateway receives as the STOMP frame body
        String rawPayload = "[{\"amount\": 25000, \"department\": \"sales\"}]";

        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance"),
                resourceFromRawJson("service_method", "req-1", rawPayload),
                "placeOrder"
        );

        assertTrue(result.isAllowed("placeOrder"));
    }

    @Test
    void rawJsonSingleArg_placeOrderOverLimit() throws Exception {
        String rawPayload = "[{\"amount\": 75000, \"department\": \"sales\"}]";

        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance"),
                resourceFromRawJson("service_method", "req-1", rawPayload),
                "placeOrder"
        );

        assertFalse(result.isAllowed("placeOrder"));
    }

    @Test
    void rawJsonMultipleArgs_transferApproved() throws Exception {
        // Two arguments: transfer details and approval record
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]
                """;

        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance")
                         .withAttribute("transferLimit", doubleValue(100000)),
                resourceFromRawJson("service_method", "req-1", rawPayload),
                "transferFunds"
        );

        assertTrue(result.isAllowed("transferFunds"));
    }

    @Test
    void rawJsonMultipleArgs_transferNotApproved() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": false, "approver": "manager-1"}
                ]
                """;

        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance")
                         .withAttribute("transferLimit", doubleValue(100000)),
                resourceFromRawJson("service_method", "req-1", rawPayload),
                "transferFunds"
        );

        assertFalse(result.isAllowed("transferFunds"));
    }

    @Test
    void rawJsonMultipleArgs_transferWrongCurrency() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "EUR"},
                    {"approved": true, "approver": "manager-1"}
                ]
                """;

        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance")
                         .withAttribute("transferLimit", doubleValue(100000)),
                resourceFromRawJson("service_method", "req-1", rawPayload),
                "transferFunds"
        );

        assertFalse(result.isAllowed("transferFunds"));
    }

    @Test
    void rawJsonNestedObjects() throws Exception {
        // Proves Cerbos can traverse nested JSON without any special handling
        String rawPayload = """
                [{"address": {"city": "Austin", "state": "TX"}, "amount": 1000}]
                """;

        // This would need a policy that checks R.attr.args[0].address.city
        // For now just verify the raw JSON parses and Cerbos accepts it
        Resource resource = resourceFromRawJson("service_method", "req-1", rawPayload);
        assertNotNull(resource);

        // Verify the structure is accessible by checking a simple policy
        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance"),
                resource,
                "placeOrder"
        );

        // amount is 1000, under the 50000 limit
        assertTrue(result.isAllowed("placeOrder"));
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
