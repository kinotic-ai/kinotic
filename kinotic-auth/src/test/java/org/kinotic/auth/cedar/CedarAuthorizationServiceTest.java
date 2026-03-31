package org.kinotic.auth.cedar;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CedarAuthorizationServiceTest {

    private static CedarAuthorizationService service;

    @BeforeAll
    static void setup() {
        service = new CedarAuthorizationService();

        // Register policies
        service.registerPolicy("placeOrder",
                "participant.roles contains 'finance' and order.amount < 50000",
                "ServiceMethod");

        service.registerPolicy("transferFunds",
                "participant.roles contains 'finance' and transfer.amount <= participant.transferLimit and transfer.currency == 'USD' and approval.approved == true",
                "ServiceMethod");

        service.registerPolicy("viewReport",
                "participant.roles contains 'admin' or participant.roles contains 'manager'",
                "ServiceMethod");
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
                service.registerPolicy("bad", "invalid @@@ expression", "ServiceMethod"));
    }

    @Test
    void unregisteredActionThrowsOnAuthorization() {
        assertThrows(CedarAuthorizationException.class, () ->
                service.isAuthorized("User", "user-1", "{\"roles\": [\"finance\"]}",
                        "nonExistent", "ServiceMethod", "req-1",
                        "[{}]", new String[]{"arg"}));
    }

    // ========== placeOrder ==========

    @Test
    void placeOrder_allowed() {
        assertTrue(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"finance\"]}",
                "placeOrder",
                "ServiceMethod", "req-1",
                "[{\"amount\": 25000, \"department\": \"sales\"}]",
                new String[]{"order"}));
    }

    @Test
    void placeOrder_denied_overLimit() {
        assertFalse(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"finance\"]}",
                "placeOrder",
                "ServiceMethod", "req-1",
                "[{\"amount\": 75000, \"department\": \"sales\"}]",
                new String[]{"order"}));
    }

    @Test
    void placeOrder_denied_wrongRole() {
        assertFalse(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"engineering\"]}",
                "placeOrder",
                "ServiceMethod", "req-1",
                "[{\"amount\": 25000}]",
                new String[]{"order"}));
    }

    // ========== transferFunds ==========

    @Test
    void transferFunds_allowed() {
        assertTrue(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds",
                "ServiceMethod", "req-1",
                "[{\"amount\": 50000, \"currency\": \"USD\"}, {\"approved\": true}]",
                new String[]{"transfer", "approval"}));
    }

    @Test
    void transferFunds_denied_overLimit() {
        assertFalse(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds",
                "ServiceMethod", "req-1",
                "[{\"amount\": 150000, \"currency\": \"USD\"}, {\"approved\": true}]",
                new String[]{"transfer", "approval"}));
    }

    @Test
    void transferFunds_denied_wrongCurrency() {
        assertFalse(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds",
                "ServiceMethod", "req-1",
                "[{\"amount\": 50000, \"currency\": \"EUR\"}, {\"approved\": true}]",
                new String[]{"transfer", "approval"}));
    }

    @Test
    void transferFunds_denied_notApproved() {
        assertFalse(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"finance\"], \"transferLimit\": 100000}",
                "transferFunds",
                "ServiceMethod", "req-1",
                "[{\"amount\": 50000, \"currency\": \"USD\"}, {\"approved\": false}]",
                new String[]{"transfer", "approval"}));
    }

    // ========== viewReport (RBAC) ==========

    @Test
    void viewReport_allowed_admin() {
        assertTrue(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"admin\"]}",
                "viewReport",
                "ServiceMethod", "req-1",
                "[{}]",
                new String[]{"params"}));
    }

    @Test
    void viewReport_allowed_manager() {
        assertTrue(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"manager\"]}",
                "viewReport",
                "ServiceMethod", "req-1",
                "[{}]",
                new String[]{"params"}));
    }

    @Test
    void viewReport_denied_regularUser() {
        assertFalse(service.isAuthorized(
                "User", "user-1",
                "{\"roles\": [\"user\"]}",
                "viewReport",
                "ServiceMethod", "req-1",
                "[{}]",
                new String[]{"params"}));
    }

    // ========== Policy lifecycle ==========

    @Test
    void removePolicy_works() {
        service.registerPolicy("temporary",
                "participant.roles contains 'admin'",
                "ServiceMethod");
        assertTrue(service.hasPolicy("temporary"));

        service.removePolicy("temporary");
        assertFalse(service.hasPolicy("temporary"));
    }
}
