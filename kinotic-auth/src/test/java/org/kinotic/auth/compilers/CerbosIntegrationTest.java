package org.kinotic.auth.compilers;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.PlanResourcesResult;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import dev.cerbos.sdk.containers.CerbosContainer;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static dev.cerbos.sdk.builders.AttributeValue.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify our ABAC requirements work with a real Cerbos PDP.
 * <p>
 * Tests cover:
 * <ul>
 *   <li>Simple RBAC (role checks, no payload)</li>
 *   <li>ABAC with entity attributes (participant + resource field comparisons)</li>
 *   <li>ABAC with positional args (R.attr.args[0].field access for service methods)</li>
 *   <li>PlanResources for generating query filters on reads</li>
 * </ul>
 */
@Testcontainers
class CerbosIntegrationTest {

    @Container
    private static final CerbosContainer cerbosContainer = new CerbosContainer()
            .withClasspathResourceMapping("policies", "/policies", org.testcontainers.containers.BindMode.READ_ONLY);

    private static CerbosBlockingClient client;

    @BeforeAll
    static void setup() throws Exception {
        client = new CerbosClientBuilder(cerbosContainer.getTarget())
                .withPlaintext()
                .buildBlockingClient();
    }

    // ========== Simple RBAC ==========

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

    @Test
    void userRoleWithoutMatchingAttributesDenied() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "user")
                         .withAttribute("department", stringValue("engineering")),
                Resource.newInstance("entity", "entity-1")
                        .withAttribute("department", stringValue("finance")),
                "read"
        );

        // Department mismatch — should be denied
        assertFalse(result.isAllowed("read"));
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
    void userCanCreateEntityUnderSpendingLimit() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "user")
                         .withAttribute("department", stringValue("engineering"))
                         .withAttribute("spendingLimit", doubleValue(10000)),
                Resource.newInstance("entity", "entity-1")
                        .withAttribute("department", stringValue("engineering"))
                        .withAttribute("amount", doubleValue(5000)),
                "create"
        );

        assertTrue(result.isAllowed("create"));
    }

    @Test
    void userCannotCreateEntityOverSpendingLimit() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "user")
                         .withAttribute("department", stringValue("engineering"))
                         .withAttribute("spendingLimit", doubleValue(10000)),
                Resource.newInstance("entity", "entity-1")
                        .withAttribute("department", stringValue("engineering"))
                        .withAttribute("amount", doubleValue(15000)),
                "create"
        );

        assertFalse(result.isAllowed("create"));
    }

    @Test
    void userCanReadEntityWithAllowedStatus() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "user")
                         .withAttribute("department", stringValue("engineering")),
                Resource.newInstance("entity", "entity-1")
                        .withAttribute("department", stringValue("finance"))
                        .withAttribute("status", stringValue("active")),
                "read"
        );

        // Department doesn't match, but status is in allowed list
        assertTrue(result.isAllowed("read"));
    }

    @Test
    void userCannotReadEntityWithDisallowedStatus() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "user")
                         .withAttribute("department", stringValue("engineering")),
                Resource.newInstance("entity", "entity-1")
                        .withAttribute("department", stringValue("finance"))
                        .withAttribute("status", stringValue("archived")),
                "read"
        );

        // Department doesn't match AND status is not in allowed list
        assertFalse(result.isAllowed("read"));
    }

    // ========== ABAC with Positional Args (Service Methods) ==========

    @Test
    void financeCanPlaceOrderUnderLimit() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance"),
                Resource.newInstance("service_method", "placeOrder-req-1")
                        .withAttribute("args", listValue(
                                // args[0] = the Order object
                                mapValue(Map.of(
                                        "amount", doubleValue(25000),
                                        "department", stringValue("sales")
                                ))
                        )),
                "placeOrder"
        );

        assertTrue(result.isAllowed("placeOrder"));
    }

    @Test
    void financeCannotPlaceOrderOverLimit() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance"),
                Resource.newInstance("service_method", "placeOrder-req-1")
                        .withAttribute("args", listValue(
                                mapValue(Map.of(
                                        "amount", doubleValue(75000),
                                        "department", stringValue("sales")
                                ))
                        )),
                "placeOrder"
        );

        assertFalse(result.isAllowed("placeOrder"));
    }

    @Test
    void financeCanTransferWithMultipleArgs() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance")
                         .withAttribute("transferLimit", doubleValue(100000)),
                Resource.newInstance("service_method", "transfer-req-1")
                        .withAttribute("args", listValue(
                                // args[0] = transfer details
                                mapValue(Map.of(
                                        "amount", doubleValue(50000),
                                        "currency", stringValue("USD")
                                )),
                                // args[1] = approval record
                                mapValue(Map.of(
                                        "approved", boolValue(true),
                                        "approver", stringValue("manager-1")
                                ))
                        )),
                "transferFunds"
        );

        assertTrue(result.isAllowed("transferFunds"));
    }

    @Test
    void financeCannotTransferWithoutApproval() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance")
                         .withAttribute("transferLimit", doubleValue(100000)),
                Resource.newInstance("service_method", "transfer-req-1")
                        .withAttribute("args", listValue(
                                mapValue(Map.of(
                                        "amount", doubleValue(50000),
                                        "currency", stringValue("USD")
                                )),
                                mapValue(Map.of(
                                        "approved", boolValue(false),
                                        "approver", stringValue("manager-1")
                                ))
                        )),
                "transferFunds"
        );

        assertFalse(result.isAllowed("transferFunds"));
    }

    @Test
    void financeCannotTransferWrongCurrency() {
        CheckResult result = client.check(
                Principal.newInstance("user-1", "finance")
                         .withAttribute("transferLimit", doubleValue(100000)),
                Resource.newInstance("service_method", "transfer-req-1")
                        .withAttribute("args", listValue(
                                mapValue(Map.of(
                                        "amount", doubleValue(50000),
                                        "currency", stringValue("EUR")
                                )),
                                mapValue(Map.of(
                                        "approved", boolValue(true),
                                        "approver", stringValue("manager-1")
                                ))
                        )),
                "transferFunds"
        );

        assertFalse(result.isAllowed("transferFunds"));
    }

    // ========== PlanResources (Query Filter Generation) ==========

    @Test
    void planResourcesForAdminReturnsAlwaysAllowed() {
        PlanResourcesResult plan = client.plan(
                Principal.newInstance("admin-user", "admin"),
                Resource.newInstance("entity"),
                "read"
        );

        assertTrue(plan.isAlwaysAllowed(), "Admin should always be allowed to read");
    }

    @Test
    void planResourcesForUserReturnsCondition() {
        PlanResourcesResult plan = client.plan(
                Principal.newInstance("user-1", "user")
                         .withAttribute("department", stringValue("engineering")),
                Resource.newInstance("entity"),
                "read"
        );

        // Should not be always allowed or always denied — there's a conditional plan
        assertFalse(plan.isAlwaysAllowed(), "User should have conditional access");
        assertFalse(plan.isAlwaysDenied(), "User should not be completely denied");

        // The plan should contain a condition we can translate to an ES query
        assertNotNull(plan.getCondition(), "Plan should have a condition for query filtering");
    }
}
