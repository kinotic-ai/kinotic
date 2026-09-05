package org.kinotic.auth.cedar;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.auth.api.engine.AuthorizationRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CedarAuthorizationServiceTest {

    private static CedarAuthorizationService service;

    @BeforeAll
    static void setup() {
        service = new CedarAuthorizationService();

        service.registerPolicy("placeOrder",
                "participant.roles contains 'finance' and order.amount < 50000");

        service.registerPolicy("transferFunds",
                "participant.roles contains 'finance' and transfer.amount <= participant.transferLimit and transfer.currency == 'USD' and approval.approved == true");

        service.registerPolicy("viewReport",
                "participant.roles contains 'admin' or participant.roles contains 'manager'");
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
        assertTrue(service.hasPolicy("transferFunds"));
        assertTrue(service.hasPolicy("viewReport"));
        assertFalse(service.hasPolicy("nonExistent"));
    }

    @Test
    void invalidExpressionThrowsOnRegistration() {
        assertThrows(CedarPolicyRegistrationException.class, () ->
                service.registerPolicy("bad", "invalid @@@ expression"));
    }

    @Test
    void unregisteredActionThrowsOnAuthorization() {
        assertThrows(CedarAuthorizationException.class, () ->
                service.isAuthorized(request("{\"roles\": [\"finance\"]}", "nonExistent", "[{}]", "arg")));
    }

    // ========== placeOrder ==========

    @Test
    void placeOrder_allowed() {
        assertTrue(service.isAuthorized(request(
                "{\"roles\": [\"finance\"]}",
                "placeOrder",
                "[{\"amount\": 25000, \"department\": \"sales\"}]",
                "order")));
    }

    @Test
    void placeOrder_denied_overLimit() {
        assertFalse(service.isAuthorized(request(
                "{\"roles\": [\"finance\"]}",
                "placeOrder",
                "[{\"amount\": 75000, \"department\": \"sales\"}]",
                "order")));
    }

    @Test
    void placeOrder_denied_wrongRole() {
        assertFalse(service.isAuthorized(request(
                "{\"roles\": [\"engineering\"]}",
                "placeOrder",
                "[{\"amount\": 25000}]",
                "order")));
    }

    // ========== transferFunds ==========

    @Test
    void transferFunds_allowed() {
        assertTrue(service.isAuthorized(request(
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds",
                "[{\"amount\": 50000, \"currency\": \"USD\"}, {\"approved\": true}]",
                "transfer", "approval")));
    }

    @Test
    void transferFunds_denied_overLimit() {
        assertFalse(service.isAuthorized(request(
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds",
                "[{\"amount\": 150000, \"currency\": \"USD\"}, {\"approved\": true}]",
                "transfer", "approval")));
    }

    @Test
    void transferFunds_denied_wrongCurrency() {
        assertFalse(service.isAuthorized(request(
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds",
                "[{\"amount\": 50000, \"currency\": \"EUR\"}, {\"approved\": true}]",
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

    // ========== viewReport (RBAC) ==========

    @Test
    void viewReport_allowed_admin() {
        assertTrue(service.isAuthorized(request(
                "{\"roles\": [\"admin\"]}", "viewReport", "[{}]", "params")));
    }

    @Test
    void viewReport_allowed_manager() {
        assertTrue(service.isAuthorized(request(
                "{\"roles\": [\"manager\"]}", "viewReport", "[{}]", "params")));
    }

    @Test
    void viewReport_denied_regularUser() {
        assertFalse(service.isAuthorized(request(
                "{\"roles\": [\"user\"]}", "viewReport", "[{}]", "params")));
    }

    // ========== Policy lifecycle ==========

    @Test
    void removePolicy_works() {
        service.registerPolicy("temporary",
                "participant.roles contains 'admin'");
        assertTrue(service.hasPolicy("temporary"));

        service.removePolicy("temporary");
        assertFalse(service.hasPolicy("temporary"));
    }
}
