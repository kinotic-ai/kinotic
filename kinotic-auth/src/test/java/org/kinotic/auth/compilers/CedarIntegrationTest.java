package org.kinotic.auth.compilers;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify Cedar can evaluate policies using raw JSON payloads
 * transformed to named args at the gateway.
 * <p>
 * Flow tested:
 * <ol>
 *   <li>Raw JSON array arrives: {@code [{"amount": 25000}, {"approved": true}]}</li>
 *   <li>Gateway transforms to named object using parameter names:
 *       {@code {"order": {"amount": 25000}, "approval": {"approved": true}}}</li>
 *   <li>Named object becomes Cedar Entity attrs via {@link Entity#parse(String)}</li>
 *   <li>Cedar evaluates the policy in-process</li>
 * </ol>
 */
class CedarIntegrationTest {

    private static AuthorizationEngine engine;

    // ========== Policies ==========

    private static final String ENTITY_POLICIES = """
            permit(
                principal,
                action,
                resource is Entity
            ) when {
                principal.roles.contains("admin")
            };

            permit(
                principal,
                action == Action::"read",
                resource is Entity
            ) when {
                principal.roles.contains("user") &&
                principal.department == resource.department
            };

            permit(
                principal,
                action == Action::"create",
                resource is Entity
            ) when {
                principal.roles.contains("user") &&
                principal.department == resource.department &&
                resource.amount < principal.spendingLimit
            };

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
    static void setup() {
        engine = new BasicAuthorizationEngine();
    }

    // ========== Helpers ==========

    private static EntityUID uid(String type, String id) {
        return new EntityUID(EntityTypeName.parse(type).get(), id);
    }

    /**
     * Builds a Cedar Entity from a JSON string containing its attributes.
     * The JSON format expected by Cedar's Entity.parse() is:
     * <pre>
     * {"uid": {"type": "TypeName", "id": "id"}, "attrs": {...}, "parents": []}
     * </pre>
     */
    private static Entity entityFromJson(String type, String id, String attrsJson) throws Exception {
        String entityJson = """
                {"uid": {"type": "%s", "id": "%s"}, "attrs": %s, "parents": []}
                """.formatted(type, id, attrsJson);
        return Entity.parse(entityJson);
    }

    /**
     * Simulates the gateway transforming a raw JSON argument array to a named object.
     * <p>
     * Input:  {@code [{"amount": 25000}, {"approved": true}]} with names {@code ["order", "approval"]}
     * Output: {@code {"order": {"amount": 25000}, "approval": {"approved": true}}}
     */
    private static String rawArgsToNamedJson(String rawJsonArray, String[] paramNames) throws Exception {
        // Use the Jackson streaming approach from the benchmark
        var mapper = new tools.jackson.databind.ObjectMapper();
        var writer = new java.io.StringWriter();
        try (var reader = mapper.createParser(rawJsonArray);
             var gen = mapper.createGenerator(writer)) {
            gen.writeStartObject();
            reader.nextToken(); // START_ARRAY
            int i = 0;
            while (reader.nextToken() != tools.jackson.core.JsonToken.END_ARRAY) {
                if (i >= paramNames.length) {
                    throw new IllegalArgumentException("More args than parameter names");
                }
                gen.writeName(paramNames[i]);
                gen.copyCurrentStructure(reader);
                i++;
            }
            gen.writeEndObject();
        }
        return writer.toString();
    }

    private static boolean isAllowed(String policyText,
                                     Entity principal,
                                     String action,
                                     Entity resource) throws Exception {
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
        Entity admin = entityFromJson("User", "admin-user", """
                {"roles": ["admin"]}""");
        Entity entity = entityFromJson("Entity", "entity-1", "{}");

        assertTrue(isAllowed(ENTITY_POLICIES, admin, "read", entity));
        assertTrue(isAllowed(ENTITY_POLICIES, admin, "create", entity));
        assertTrue(isAllowed(ENTITY_POLICIES, admin, "delete", entity));
    }

    @Test
    void userRoleWithoutMatchingAttributesDenied() throws Exception {
        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["user"], "department": "engineering"}""");
        Entity entity = entityFromJson("Entity", "entity-1", """
                {"department": "finance"}""");

        assertFalse(isAllowed(ENTITY_POLICIES, user, "read", entity));
    }

    // ========== ABAC with Entity Attributes ==========

    @Test
    void userCanReadEntityInSameDepartment() throws Exception {
        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["user"], "department": "engineering"}""");
        Entity entity = entityFromJson("Entity", "entity-1", """
                {"department": "engineering"}""");

        assertTrue(isAllowed(ENTITY_POLICIES, user, "read", entity));
    }

    @Test
    void userCanCreateEntityUnderSpendingLimit() throws Exception {
        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["user"], "department": "engineering", "spendingLimit": 10000}""");
        Entity entity = entityFromJson("Entity", "entity-1", """
                {"department": "engineering", "amount": 5000}""");

        assertTrue(isAllowed(ENTITY_POLICIES, user, "create", entity));
    }

    @Test
    void userCannotCreateEntityOverSpendingLimit() throws Exception {
        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["user"], "department": "engineering", "spendingLimit": 10000}""");
        Entity entity = entityFromJson("Entity", "entity-1", """
                {"department": "engineering", "amount": 15000}""");

        assertFalse(isAllowed(ENTITY_POLICIES, user, "create", entity));
    }

    @Test
    void userCanReadEntityWithAllowedStatus() throws Exception {
        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["user"], "department": "engineering"}""");
        Entity entity = entityFromJson("Entity", "entity-1", """
                {"department": "finance", "status": "active"}""");

        assertTrue(isAllowed(ENTITY_POLICIES, user, "read", entity));
    }

    @Test
    void userCannotReadEntityWithDisallowedStatus() throws Exception {
        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["user"], "department": "engineering"}""");
        Entity entity = entityFromJson("Entity", "entity-1", """
                {"department": "finance", "status": "archived"}""");

        assertFalse(isAllowed(ENTITY_POLICIES, user, "read", entity));
    }

    // ========== ABAC with Raw JSON → Named Args (Service Methods) ==========

    @Test
    void rawJson_financeCanPlaceOrderUnderLimit() throws Exception {
        String rawPayload = "[{\"amount\": 25000, \"department\": \"sales\"}]";
        String namedAttrs = rawArgsToNamedJson(rawPayload, new String[]{"order"});

        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["finance"]}""");
        Entity method = entityFromJson("ServiceMethod", "req-1", namedAttrs);

        assertTrue(isAllowed(SERVICE_POLICIES, user, "placeOrder", method));
    }

    @Test
    void rawJson_financeCannotPlaceOrderOverLimit() throws Exception {
        String rawPayload = "[{\"amount\": 75000, \"department\": \"sales\"}]";
        String namedAttrs = rawArgsToNamedJson(rawPayload, new String[]{"order"});

        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["finance"]}""");
        Entity method = entityFromJson("ServiceMethod", "req-1", namedAttrs);

        assertFalse(isAllowed(SERVICE_POLICIES, user, "placeOrder", method));
    }

    @Test
    void rawJson_financeCanTransferWithMultipleArgs() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]""";
        String namedAttrs = rawArgsToNamedJson(rawPayload, new String[]{"transfer", "approval"});

        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["finance"], "transferLimit": 100000}""");
        Entity method = entityFromJson("ServiceMethod", "req-1", namedAttrs);

        assertTrue(isAllowed(SERVICE_POLICIES, user, "transferFunds", method));
    }

    @Test
    void rawJson_financeCannotTransferWithoutApproval() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "USD"},
                    {"approved": false, "approver": "manager-1"}
                ]""";
        String namedAttrs = rawArgsToNamedJson(rawPayload, new String[]{"transfer", "approval"});

        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["finance"], "transferLimit": 100000}""");
        Entity method = entityFromJson("ServiceMethod", "req-1", namedAttrs);

        assertFalse(isAllowed(SERVICE_POLICIES, user, "transferFunds", method));
    }

    @Test
    void rawJson_financeCannotTransferWrongCurrency() throws Exception {
        String rawPayload = """
                [
                    {"amount": 50000, "currency": "EUR"},
                    {"approved": true, "approver": "manager-1"}
                ]""";
        String namedAttrs = rawArgsToNamedJson(rawPayload, new String[]{"transfer", "approval"});

        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["finance"], "transferLimit": 100000}""");
        Entity method = entityFromJson("ServiceMethod", "req-1", namedAttrs);

        assertFalse(isAllowed(SERVICE_POLICIES, user, "transferFunds", method));
    }

    @Test
    void rawJson_financeCannotTransferOverLimit() throws Exception {
        String rawPayload = """
                [
                    {"amount": 150000, "currency": "USD"},
                    {"approved": true, "approver": "manager-1"}
                ]""";
        String namedAttrs = rawArgsToNamedJson(rawPayload, new String[]{"transfer", "approval"});

        Entity user = entityFromJson("User", "user-1", """
                {"roles": ["finance"], "transferLimit": 100000}""");
        Entity method = entityFromJson("ServiceMethod", "req-1", namedAttrs);

        assertFalse(isAllowed(SERVICE_POLICIES, user, "transferFunds", method));
    }
}
