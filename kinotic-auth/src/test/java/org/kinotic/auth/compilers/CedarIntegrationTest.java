package org.kinotic.auth.compilers;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify Cedar can evaluate policies using the named-args approach.
 * <p>
 * Instead of a JSON array {@code [{"amount": 25000}, {"approved": true}]},
 * the gateway transforms the payload to a named object:
 * {@code {"order": {"amount": 25000}, "approval": {"approved": true}}}
 * <p>
 * Cedar resource attributes then directly map to parameter names,
 * enabling policies like {@code resource.order.amount < 50000}.
 * <p>
 * This test mirrors the Cerbos integration tests to enable a direct comparison.
 */
class CedarIntegrationTest {

    private static AuthorizationEngine engine;

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

    // ========== Helpers ==========

    private static PolicySet policies(String policyText) {
        return PolicySet.parsePolicies(policyText);
    }

    private static EntityUID uid(String type, String id) {
        return new EntityUID(EntityTypeName.parse(type).get(), id);
    }

    private static Entity principal(String id, Map<String, Value> attrs) {
        return new Entity(uid("User", id), attrs, Set.of());
    }

    private static Entity resource(String type, String id, Map<String, Value> attrs) {
        return new Entity(uid(type, id), attrs, Set.of());
    }

    private static boolean isAllowed(String policyText,
                                     Entity principal,
                                     String action,
                                     Entity resource) {
        Set<Entity> entities = new HashSet<>();
        entities.add(principal);
        entities.add(resource);

        AuthorizationRequest request = new AuthorizationRequest(
                principal.getEUID(),
                uid("Action", action),
                resource.getEUID(),
                Map.of()
        );

        AuthorizationResponse response = engine.isAuthorized(request, policies(policyText), entities);
        assertEquals(AuthorizationResponse.SuccessOrFailure.Success, response.type,
                     "Authorization evaluation failed: " + response);
        return response.success.get().isAllowed();
    }

    /**
     * Converts a Java map to a Cedar CedarMap Value (for nested resource attributes).
     */
    private static CedarMap cedarMap(Map<String, Value> map) {
        return new CedarMap(map);
    }

    // ========== Simple RBAC ==========

    @Test
    void adminRoleAllowsAllActions() {
        Entity admin = principal("admin-user", Map.of(
                "roles", new CedarList(List.of(new PrimString("admin")))
        ));
        Entity entity = resource("Entity", "entity-1", Map.of());

        assertTrue(isAllowed(ENTITY_POLICIES, admin, "read", entity));
        assertTrue(isAllowed(ENTITY_POLICIES, admin, "create", entity));
        assertTrue(isAllowed(ENTITY_POLICIES, admin, "delete", entity));
    }

    @Test
    void userRoleWithoutMatchingAttributesDenied() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("user"))),
                "department", new PrimString("engineering")
        ));
        Entity entity = resource("Entity", "entity-1", Map.of(
                "department", new PrimString("finance")
        ));

        assertFalse(isAllowed(ENTITY_POLICIES, user, "read", entity));
    }

    // ========== ABAC with Entity Attributes ==========

    @Test
    void userCanReadEntityInSameDepartment() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("user"))),
                "department", new PrimString("engineering")
        ));
        Entity entity = resource("Entity", "entity-1", Map.of(
                "department", new PrimString("engineering")
        ));

        assertTrue(isAllowed(ENTITY_POLICIES, user, "read", entity));
    }

    @Test
    void userCanCreateEntityUnderSpendingLimit() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("user"))),
                "department", new PrimString("engineering"),
                "spendingLimit", new PrimLong(10000)
        ));
        Entity entity = resource("Entity", "entity-1", Map.of(
                "department", new PrimString("engineering"),
                "amount", new PrimLong(5000)
        ));

        assertTrue(isAllowed(ENTITY_POLICIES, user, "create", entity));
    }

    @Test
    void userCannotCreateEntityOverSpendingLimit() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("user"))),
                "department", new PrimString("engineering"),
                "spendingLimit", new PrimLong(10000)
        ));
        Entity entity = resource("Entity", "entity-1", Map.of(
                "department", new PrimString("engineering"),
                "amount", new PrimLong(15000)
        ));

        assertFalse(isAllowed(ENTITY_POLICIES, user, "create", entity));
    }

    @Test
    void userCanReadEntityWithAllowedStatus() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("user"))),
                "department", new PrimString("engineering")
        ));
        Entity entity = resource("Entity", "entity-1", Map.of(
                "department", new PrimString("finance"),
                "status", new PrimString("active")
        ));

        assertTrue(isAllowed(ENTITY_POLICIES, user, "read", entity));
    }

    @Test
    void userCannotReadEntityWithDisallowedStatus() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("user"))),
                "department", new PrimString("engineering")
        ));
        Entity entity = resource("Entity", "entity-1", Map.of(
                "department", new PrimString("finance"),
                "status", new PrimString("archived")
        ));

        assertFalse(isAllowed(ENTITY_POLICIES, user, "read", entity));
    }

    // ========== ABAC with Named Args (Service Methods) ==========

    @Test
    void financeCanPlaceOrderUnderLimit() {
        // Named args: {"order": {"amount": 25000, "department": "sales"}}
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("finance")))
        ));
        Entity method = resource("ServiceMethod", "req-1", Map.of(
                "order", cedarMap(Map.of(
                        "amount", new PrimLong(25000),
                        "department", new PrimString("sales")
                ))
        ));

        assertTrue(isAllowed(SERVICE_POLICIES, user, "placeOrder", method));
    }

    @Test
    void financeCannotPlaceOrderOverLimit() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("finance")))
        ));
        Entity method = resource("ServiceMethod", "req-1", Map.of(
                "order", cedarMap(Map.of(
                        "amount", new PrimLong(75000),
                        "department", new PrimString("sales")
                ))
        ));

        assertFalse(isAllowed(SERVICE_POLICIES, user, "placeOrder", method));
    }

    @Test
    void financeCanTransferWithMultipleNamedArgs() {
        // Named args: {"transfer": {"amount": 50000, "currency": "USD"}, "approval": {"approved": true}}
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("finance"))),
                "transferLimit", new PrimLong(100000)
        ));
        Entity method = resource("ServiceMethod", "req-1", Map.of(
                "transfer", cedarMap(Map.of(
                        "amount", new PrimLong(50000),
                        "currency", new PrimString("USD")
                )),
                "approval", cedarMap(Map.of(
                        "approved", new PrimBool(true),
                        "approver", new PrimString("manager-1")
                ))
        ));

        assertTrue(isAllowed(SERVICE_POLICIES, user, "transferFunds", method));
    }

    @Test
    void financeCannotTransferWithoutApproval() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("finance"))),
                "transferLimit", new PrimLong(100000)
        ));
        Entity method = resource("ServiceMethod", "req-1", Map.of(
                "transfer", cedarMap(Map.of(
                        "amount", new PrimLong(50000),
                        "currency", new PrimString("USD")
                )),
                "approval", cedarMap(Map.of(
                        "approved", new PrimBool(false),
                        "approver", new PrimString("manager-1")
                ))
        ));

        assertFalse(isAllowed(SERVICE_POLICIES, user, "transferFunds", method));
    }

    @Test
    void financeCannotTransferWrongCurrency() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("finance"))),
                "transferLimit", new PrimLong(100000)
        ));
        Entity method = resource("ServiceMethod", "req-1", Map.of(
                "transfer", cedarMap(Map.of(
                        "amount", new PrimLong(50000),
                        "currency", new PrimString("EUR")
                )),
                "approval", cedarMap(Map.of(
                        "approved", new PrimBool(true),
                        "approver", new PrimString("manager-1")
                ))
        ));

        assertFalse(isAllowed(SERVICE_POLICIES, user, "transferFunds", method));
    }

    @Test
    void financeCannotTransferOverLimit() {
        Entity user = principal("user-1", Map.of(
                "roles", new CedarList(List.of(new PrimString("finance"))),
                "transferLimit", new PrimLong(100000)
        ));
        Entity method = resource("ServiceMethod", "req-1", Map.of(
                "transfer", cedarMap(Map.of(
                        "amount", new PrimLong(150000),
                        "currency", new PrimString("USD")
                )),
                "approval", cedarMap(Map.of(
                        "approved", new PrimBool(true),
                        "approver", new PrimString("manager-1")
                ))
        ));

        assertFalse(isAllowed(SERVICE_POLICIES, user, "transferFunds", method));
    }
}
