package org.kinotic.auth.spel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.auth.api.engine.AuthorizationRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpelAuthorizationServiceTest {

    /** A DSL string literal whose value, if it ever escaped its quotes, would be executable SpEL. */
    private static final String HOSTILE_LITERAL = "x\\') || T(java.lang.Runtime).getRuntime().exec(\\'calc\\') || (\\'y";
    private static final String HOSTILE_VALUE = "x') || T(java.lang.Runtime).getRuntime().exec('calc') || ('y";

    private static SpelAuthorizationService service;

    @BeforeAll
    static void setup() {
        service = new SpelAuthorizationService();

        service.registerPolicy("placeOrder",
                "participant.roles contains 'finance' and order.amount < 50000");
        service.registerPolicy("transferFunds",
                "participant.roles contains 'finance' and transfer.amount <= participant.transferLimit and transfer.currency == 'USD' and approval.approved == true");
        service.registerPolicy("viewReport",
                "participant.roles contains 'admin' or participant.roles contains 'manager'");
        service.registerPolicy("contactUser", "user.email like '*@kinotic.ai'");
        service.registerPolicy("approveDoc", "doc.approvedBy exists");
        service.registerPolicy("viewDoc", "doc.status in ['active', 'pending']");
        service.registerPolicy("nameCheck", "participant.name == '" + HOSTILE_LITERAL + "'");
    }

    private static AuthorizationRequest request(String principalAttributesJson,
                                                String action,
                                                String argumentsJson,
                                                String... parameterNames) {
        return new AuthorizationRequest("user-1", principalAttributesJson, action, argumentsJson, List.of(parameterNames));
    }

    // ========== Policy Registration ==========

    @Test
    void registeredPoliciesAreAccessible() {
        assertTrue(service.hasPolicy("placeOrder"));
        assertFalse(service.hasPolicy("nonExistent"));
    }

    @Test
    void invalidExpressionThrowsOnRegistration() {
        assertThrows(SpelPolicyRegistrationException.class, () ->
                service.registerPolicy("bad", "invalid @@@ expression"));
    }

    @Test
    void unregisteredActionThrowsOnAuthorization() {
        assertThrows(SpelAuthorizationException.class, () ->
                service.isAuthorized(request("{\"roles\": [\"finance\"]}", "nonExistent", "[{}]", "arg")));
    }

    // ========== Decisions ==========

    @Test
    void placeOrder_allowed() {
        assertTrue(service.isAuthorized(request("{\"roles\": [\"finance\"]}", "placeOrder", "[{\"amount\": 25000}]", "order")));
    }

    @Test
    void placeOrder_denied_overLimit() {
        assertFalse(service.isAuthorized(request("{\"roles\": [\"finance\"]}", "placeOrder", "[{\"amount\": 75000}]", "order")));
    }

    @Test
    void placeOrder_denied_wrongRole() {
        assertFalse(service.isAuthorized(request("{\"roles\": [\"engineering\"]}", "placeOrder", "[{\"amount\": 25000}]", "order")));
    }

    @Test
    void placeOrder_denied_whenAmountIsMissing() {
        assertFalse(service.isAuthorized(request("{\"roles\": [\"finance\"]}", "placeOrder", "[{\"department\": \"sales\"}]", "order")));
    }

    @Test
    void placeOrder_denied_whenArgumentIsMissing() {
        assertFalse(service.isAuthorized(request("{\"roles\": [\"finance\"]}", "placeOrder", "[]", "order")));
    }

    @Test
    void transferFunds_allowed() {
        assertTrue(service.isAuthorized(request(
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds",
                "[{\"amount\": 50000, \"currency\": \"USD\"}, {\"approved\": true}]",
                "transfer", "approval")));
    }

    @Test
    void transferFunds_denied_notApproved() {
        assertFalse(service.isAuthorized(request(
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds",
                "[{\"amount\": 50000, \"currency\": \"USD\"}, {\"approved\": false}]",
                "transfer", "approval")));
    }

    @Test
    void viewReport_allowsEitherRole() {
        assertTrue(service.isAuthorized(request("{\"roles\": [\"manager\"]}", "viewReport", "[{}]", "params")));
        assertFalse(service.isAuthorized(request("{\"roles\": [\"user\"]}", "viewReport", "[{}]", "params")));
    }

    @Test
    void contactUser_matchesGlob() {
        assertTrue(service.isAuthorized(request("{}", "contactUser", "[{\"email\": \"navid@kinotic.ai\"}]", "user")));
        assertFalse(service.isAuthorized(request("{}", "contactUser", "[{\"email\": \"navid@gmail.com\"}]", "user")));
    }

    @Test
    void approveDoc_requiresTheAttribute() {
        assertTrue(service.isAuthorized(request("{}", "approveDoc", "[{\"approvedBy\": \"mgr-1\"}]", "doc")));
        assertFalse(service.isAuthorized(request("{}", "approveDoc", "[{\"title\": \"q3\"}]", "doc")));
    }

    @Test
    void viewDoc_matchesAnyListedStatus() {
        assertTrue(service.isAuthorized(request("{}", "viewDoc", "[{\"status\": \"pending\"}]", "doc")));
        assertFalse(service.isAuthorized(request("{}", "viewDoc", "[{\"status\": \"archived\"}]", "doc")));
    }

    @Test
    void preParsedInputsEvaluateTheSamePolicy() {
        assertTrue(service.isAuthorized("placeOrder", Map.of("roles", List.of("finance")), Map.of("order", Map.of("amount", 25000L))));
        assertFalse(service.isAuthorized("placeOrder", Map.of("roles", List.of("finance")), Map.of("order", Map.of("amount", 75000L))));
    }

    // ========== Hostile input is data ==========

    @Test
    void hostilePolicyLiteralIsComparedAsData() {
        assertFalse(service.isAuthorized(request("{\"name\": \"x\"}", "nameCheck", "[{}]", "params")));
        assertTrue(service.isAuthorized("nameCheck", Map.of("name", HOSTILE_VALUE), Map.of("params", Map.of())));
    }

    @Test
    void hostileAttributeValuesAreComparedAsData() {
        String attributes = "{\"roles\": [\"#{T(java.lang.Runtime).getRuntime().exec('calc')}\", \"${sub}\"]}";
        assertFalse(service.isAuthorized(request(attributes, "placeOrder", "[{\"amount\": 25000}]", "order")));
        assertFalse(service.isAuthorized(request(attributes, "viewReport", "[{}]", "params")));
    }

    @Test
    void hostileArgumentValuesAreComparedAsData() {
        // The value is only ever a string: it matches the glob when it ends in the domain, and not otherwise.
        assertTrue(service.isAuthorized(request("{}", "contactUser",
                "[{\"email\": \"T(java.lang.Runtime).getRuntime()@kinotic.ai\"}]", "user")));
        assertFalse(service.isAuthorized(request("{}", "contactUser",
                "[{\"email\": \"#{T(java.lang.Runtime)}@evil.com\"}]", "user")));
    }
}
