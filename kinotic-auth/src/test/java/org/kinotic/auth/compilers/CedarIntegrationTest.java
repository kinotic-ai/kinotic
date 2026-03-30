package org.kinotic.auth.compilers;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.CedarJson;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify Cedar can evaluate policies using raw JSON payloads
 * transformed to named args at the gateway.
 * <p>
 * Flow tested:
 * <ol>
 *   <li>Raw JSON array arrives: {@code [{"amount": 25000}, {"approved": true}]}</li>
 *   <li>Gateway transforms to named object using parameter names: {@code {"order": {"amount": 25000}, "approval": {"approved": true}}}</li>
 *   <li>Named object is deserialized to Cedar {@code Value} types</li>
 *   <li>Cedar evaluates the policy in-process</li>
 * </ol>
 */
class CedarIntegrationTest {

    private static AuthorizationEngine engine;
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // ========== Policies ==========

    private static final String ENTITY_POLICIES = """
            // Admin role can do anything
            permit(
                principal,
                action,
                resource is Entity
            ) when {
                principal.roles.contains("admin")
            };

            // Users can read entities in their own department
            permit(
                principal,
                action == Action::"read",
                resource is Entity
            ) when {
                principal.roles.contains("user") &&
                principal.department == resource.department
            };

            // Users can create entities under their spending limit in same department
            permit(
                principal,
                action == Action::"create",
                resource is Entity
            ) when {
                principal.roles.contains("user") &&
                principal.department == resource.department &&
                resource.amount < principal.spendingLimit
            };

            // Users can read entities with allowed statuses
            permit(
                principal,
                action == Action::"read",
                resource is Entity
            ) when {
                principal.roles.contains("user") &&
                ["active", "pending"].contains(resource.status)
            };
            """;

    private static final String SERVICE_POLICIES = """
            // Finance role can place orders under 50000
            permit(
                principal,
                action == Action::"placeOrder",
                resource is ServiceMethod
            ) when {
                principal.roles.contains("finance") &&
                resource.order.amount < 50000
            };

            // Finance role can transfer funds with multiple conditions
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
    static void setup() {
        engine = new BasicAuthorizationEngine();
    }

    // ========== Helpers: Raw JSON → Cedar ==========

    /**
     * Transforms a raw JSON array to a named object using parameter names,
     * then deserializes to Cedar Value types. This is the full gateway flow.
     *
     * @param rawJsonArray e.g. {@code [{"amount": 25000}, {"approved": true}]}
     * @param paramNames   e.g. {@code ["order", "approval"]}
     * @return Cedar attribute map e.g. {@code {"order": {"amount": 25000}, "approval": {"approved": true}}}
     */
    private static Map<String, Value> rawJsonToResourceAttrs(String rawJsonArray, String[] paramNames) throws Exception {
        // Step 1: Parse the raw JSON array
        JsonNode arrayNode = JSON_MAPPER.readTree(rawJsonArray);
        assertTrue(arrayNode.isArray(), "Payload must be a JSON array");
        assertEquals(paramNames.length, arrayNode.size(), "Parameter count mismatch");

        // Step 2: Build a named JSON object from the array + param names
        Map<String, JsonNode> namedMap = new LinkedHashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            namedMap.put(paramNames[i], arrayNode.get(i));
        }

        // Step 3: Convert each named entry to a Cedar Value using CedarJson deserializer
        com.fasterxml.jackson.databind.ObjectReader cedarReader = CedarJson.objectReader().forType(Value.class);
        Map<String, Value> attrs = new HashMap<>();
        for (var entry : namedMap.entrySet()) {
            Value cedarValue = cedarReader.readValue(entry.getValue().toString());
            attrs.put(entry.getKey(), cedarValue);
        }
        return attrs;
    }

    /**
     * Converts a simple JSON object string to Cedar Value attributes.
     * Used for entity resources and principals where the JSON is already an object.
     */
    private static Map<String, Value> jsonToAttrs(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectReader cedarReader = CedarJson.objectReader().forType(Value.class);
        JsonNode node = JSON_MAPPER.readTree(json);
        Map<String, Value> attrs = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            try {
                attrs.put(entry.getKey(), cedarReader.readValue(entry.getValue().toString()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return attrs;
    }

    private static EntityUID uid(String type, String id) {
        return new EntityUID(EntityTypeName.parse(type).get(), id);
    }

    private static boolean isAllowed(String policyText,
                                     Map<String, Value> principalAttrs,
                                     String action,
                                     String resourceType,
                                     String resourceId,
                                     Map<String, Value> resourceAttrs) throws Exception {
        Entity principal = new Entity(uid("User", "user"), principalAttrs, Set.of());
        Entity resource = new Entity(uid(resourceType, resourceId), resourceAttrs, Set.of());

        Set<Entity> entities = new HashSet<>();
        entities.add(principal);
        entities.add(resource);

        AuthorizationRequest request = new AuthorizationRequest(
                principal.getEUID(),
                uid("Action", action),
                resource.getEUID(),
                Map.of()
        );

        PolicySet policies = PolicySet.parsePolicies(policyText);
        AuthorizationResponse response = engine.isAuthorized(request, policies, entities);
        assertEquals(AuthorizationResponse.SuccessOrFailure.Success, response.type,
                     "Authorization evaluation failed: " + response);
        return response.success.get().isAllowed();
    }

    // ========== Simple RBAC ==========

    @Test
    void adminRoleAllowsAllActions() throws Exception {
        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["admin"]}
                """);

        assertTrue(isAllowed(ENTITY_POLICIES, principalAttrs, "read", "Entity", "e1", Map.of()));
        assertTrue(isAllowed(ENTITY_POLICIES, principalAttrs, "create", "Entity", "e1", Map.of()));
        assertTrue(isAllowed(ENTITY_POLICIES, principalAttrs, "delete", "Entity", "e1", Map.of()));
    }

    @Test
    void userRoleWithoutMatchingAttributesDenied() throws Exception {
        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["user"], "department": "engineering"}
                """);
        Map<String, Value> resourceAttrs = jsonToAttrs("""
                {"department": "finance"}
                """);

        assertFalse(isAllowed(ENTITY_POLICIES, principalAttrs, "read", "Entity", "e1", resourceAttrs));
    }

    // ========== ABAC with Entity Attributes ==========

    @Test
    void userCanReadEntityInSameDepartment() throws Exception {
        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["user"], "department": "engineering"}
                """);
        Map<String, Value> resourceAttrs = jsonToAttrs("""
                {"department": "engineering"}
                """);

        assertTrue(isAllowed(ENTITY_POLICIES, principalAttrs, "read", "Entity", "e1", resourceAttrs));
    }

    @Test
    void userCanCreateEntityUnderSpendingLimit() throws Exception {
        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["user"], "department": "engineering", "spendingLimit": 10000}
                """);
        Map<String, Value> resourceAttrs = jsonToAttrs("""
                {"department": "engineering", "amount": 5000}
                """);

        assertTrue(isAllowed(ENTITY_POLICIES, principalAttrs, "create", "Entity", "e1", resourceAttrs));
    }

    @Test
    void userCannotCreateEntityOverSpendingLimit() throws Exception {
        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["user"], "department": "engineering", "spendingLimit": 10000}
                """);
        Map<String, Value> resourceAttrs = jsonToAttrs("""
                {"department": "engineering", "amount": 15000}
                """);

        assertFalse(isAllowed(ENTITY_POLICIES, principalAttrs, "create", "Entity", "e1", resourceAttrs));
    }

    @Test
    void userCanReadEntityWithAllowedStatus() throws Exception {
        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["user"], "department": "engineering"}
                """);
        Map<String, Value> resourceAttrs = jsonToAttrs("""
                {"department": "finance", "status": "active"}
                """);

        assertTrue(isAllowed(ENTITY_POLICIES, principalAttrs, "read", "Entity", "e1", resourceAttrs));
    }

    @Test
    void userCannotReadEntityWithDisallowedStatus() throws Exception {
        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["user"], "department": "engineering"}
                """);
        Map<String, Value> resourceAttrs = jsonToAttrs("""
                {"department": "finance", "status": "archived"}
                """);

        assertFalse(isAllowed(ENTITY_POLICIES, principalAttrs, "read", "Entity", "e1", resourceAttrs));
    }

    // ========== ABAC with Raw JSON → Named Args (Service Methods) ==========

    @Test
    void rawJson_financeCanPlaceOrderUnderLimit() throws Exception {
        // Raw payload as the gateway receives it
        String rawPayload = """
                [{"amount": 25000, "department": "sales"}]
                """;
        String[] paramNames = {"order"};

        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["finance"]}
                """);
        Map<String, Value> resourceAttrs = rawJsonToResourceAttrs(rawPayload, paramNames);

        assertTrue(isAllowed(SERVICE_POLICIES, principalAttrs, "placeOrder", "ServiceMethod", "req-1", resourceAttrs));
    }

    @Test
    void rawJson_financeCannotPlaceOrderOverLimit() throws Exception {
        String rawPayload = """
                [{"amount": 75000, "department": "sales"}]
                """;
        String[] paramNames = {"order"};

        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["finance"]}
                """);
        Map<String, Value> resourceAttrs = rawJsonToResourceAttrs(rawPayload, paramNames);

        assertFalse(isAllowed(SERVICE_POLICIES, principalAttrs, "placeOrder", "ServiceMethod", "req-1", resourceAttrs));
    }

    @Test
    void rawJson_financeCanTransferWithMultipleArgs() throws Exception {
        // Two args: transfer details and approval record
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]
                """;
        String[] paramNames = {"transfer", "approval"};

        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["finance"], "transferLimit": 100000}
                """);
        Map<String, Value> resourceAttrs = rawJsonToResourceAttrs(rawPayload, paramNames);

        assertTrue(isAllowed(SERVICE_POLICIES, principalAttrs, "transferFunds", "ServiceMethod", "req-1", resourceAttrs));
    }

    @Test
    void rawJson_financeCannotTransferWithoutApproval() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": false, "approver": "manager-1"}
                ]
                """;
        String[] paramNames = {"transfer", "approval"};

        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["finance"], "transferLimit": 100000}
                """);
        Map<String, Value> resourceAttrs = rawJsonToResourceAttrs(rawPayload, paramNames);

        assertFalse(isAllowed(SERVICE_POLICIES, principalAttrs, "transferFunds", "ServiceMethod", "req-1", resourceAttrs));
    }

    @Test
    void rawJson_financeCannotTransferWrongCurrency() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "EUR"},
                    {"approved": true, "approver": "manager-1"}
                ]
                """;
        String[] paramNames = {"transfer", "approval"};

        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["finance"], "transferLimit": 100000}
                """);
        Map<String, Value> resourceAttrs = rawJsonToResourceAttrs(rawPayload, paramNames);

        assertFalse(isAllowed(SERVICE_POLICIES, principalAttrs, "transferFunds", "ServiceMethod", "req-1", resourceAttrs));
    }

    @Test
    void rawJson_financeCannotTransferOverLimit() throws Exception {
        String rawPayload = """
                [
                    {"amount": 150000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]
                """;
        String[] paramNames = {"transfer", "approval"};

        Map<String, Value> principalAttrs = jsonToAttrs("""
                {"roles": ["finance"], "transferLimit": 100000}
                """);
        Map<String, Value> resourceAttrs = rawJsonToResourceAttrs(rawPayload, paramNames);

        assertFalse(isAllowed(SERVICE_POLICIES, principalAttrs, "transferFunds", "ServiceMethod", "req-1", resourceAttrs));
    }
}
