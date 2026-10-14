package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.kinotic.domain.api.config.OpenFgaProperties;
import org.kinotic.domain.api.model.security.authorization.*;
import org.kinotic.domain.internal.model.security.AuthorizationTuple;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Run against a disposable real OpenFGA 1.10+ server; stores are intentionally isolated per test run. */
@EnabledIfEnvironmentVariable(named = "OPENFGA_TEST_URL", matches = ".+")
class OpenFgaIntegrationTest {
    @Test void realModelHandlesExactGroupsDeniesRevocationAndIndependentApps() throws Exception {
        var vertx = Vertx.vertx();
        var properties = new OpenFgaProperties(); properties.setEndpoint(URI.create(System.getenv("OPENFGA_TEST_URL")));
        properties.setApiToken(System.getenv("OPENFGA_TEST_TOKEN")); properties.setCheckTimeout(Duration.ofSeconds(5));
        var client = new OpenFgaClient(vertx, properties);
        try {
            String store = await(client.createStore("Kinotic authorization integration test"));
            String model = await(client.writeModel(store, AuthorizationCompiler.model(List.of("invoice"))));
            var scope = new AuthorizationScope().setKind(AuthorizationScopeKind.APPLICATION_TENANT).setOrganizationId("acme").setApplicationId("billing").setTenantId("tenant-a");
            var role = new AuthorizationRole().setId("approver").setPermissions(List.of("invoices.approve"));
            var assignment = new AuthorizationAssignment().setId("assignment").setRoleId("approver").setSubjectKind(AuthorizationSubjectKind.GROUP).setSubjectId("approvers")
                    .setResourceType("invoice").setSelector(AuthorizationSelector.EXACT).setResourceId("one");
            var policy = new AuthorizationPolicy().setScope(scope).setRoles(List.of(role)).setGroups(List.of(new AuthorizationGroup().setId("approvers").setMemberIds(List.of("alice"))))
                    .setAssignments(List.of(assignment));
            var catalog = List.of(new AuthorizationPermission().setPermission("invoices.approve").setResourceType("invoice"));
            var allow = AuthorizationCompiler.project(policy, catalog);
            await(client.reconcile(store, model, allow, allow)); await(client.reconcile(store, model, allow, allow));
            assertTrue(await(check(client, store, model, scope, "one")));
            assertFalse(await(check(client, store, model, scope, "two")));
            var otherTenant = new AuthorizationScope().setKind(AuthorizationScopeKind.APPLICATION_TENANT).setOrganizationId("acme").setApplicationId("billing").setTenantId("tenant-b");
            assertFalse(await(check(client, store, model, otherTenant, "one")));
            assignment.setEffect(AuthorizationEffect.DENY);
            var denied = AuthorizationCompiler.project(policy, catalog);
            var combined = new java.util.ArrayList<>(allow); combined.addAll(denied);
            await(client.reconcile(store, model, combined, combined));
            assertFalse(await(check(client, store, model, scope, "one")));
            await(client.reconcile(store, model, combined, List.of())); await(client.reconcile(store, model, combined, List.of()));
            assertFalse(await(check(client, store, model, scope, "one")));
            String secondStore = await(client.createStore("Kinotic independent app integration test"));
            String secondModel = await(client.writeModel(secondStore, AuthorizationCompiler.model(List.of("ticket"))));
            // Updating a different application's types does not update the first application's model.
            await(client.reconcile(store, model, allow, allow));
            assertTrue(await(check(client, store, model, scope, "one")));
            assertNotEquals(model, secondModel);
        } finally { client.close(); await(vertx.close()); }
    }
    private Future<Boolean> check(OpenFgaClient client, String store, String model, AuthorizationScope scope, String id) {
        return client.check(store, model, new AuthorizationTuple(AuthorizationCompiler.user("alice"), "access", AuthorizationCompiler.resource(scope, "invoice", id, "invoices.approve")), AuthorizationCompiler.parents(scope, "invoice", id, "invoices.approve"));
    }
    private <T> T await(Future<T> value) throws Exception { return value.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS); }
}
