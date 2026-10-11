package org.kinotic.persistence.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.persistence.DecoratedProperty;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.EntityOperation;
import org.kinotic.domain.api.model.persistence.idl.decorators.PolicyDecorator;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.NamedQueryOperation;
import org.kinotic.idl.api.schema.decorators.RequirePermissionC3Decorator;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.internal.api.model.DefaultEntityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFgaAuthorizationServiceFactoryTest {
    private final DefaultApplicationParticipant actor = new DefaultApplicationParticipant("alice", "acme", "billing", "tenant-a", Map.of(), List.of());
    private final List<String> permissions = new ArrayList<>();
    private Vertx vertx;
    private SecurityContext security;
    private OpenFgaAuthorizationServiceFactory factory;
    private EntityDefinitionRepository entities;

    @BeforeEach void setup() {
        security = new SecurityContext();
        vertx = Vertx.vertx();
        entities = mock(EntityDefinitionRepository.class);
        factory = new OpenFgaAuthorizationServiceFactory((participant, scope, type, id, permission) -> {
            assertEquals(actor.getId(), participant.getId());
            assertEquals("acme", scope.getOrganizationId());
            assertEquals("tenant-a", scope.getTenantId());
            assertEquals("acme.billing.invoice", id);
            permissions.add(permission);
            return Future.succeededFuture();
        }, security, entities);
    }

    @AfterEach void close() throws Exception { await(vertx.close()); }

    @Test void authorizesTheEntityDefinitionOperationForTheAuthenticatedTenant() throws Exception {
        await(inContext(() -> authorize(definition(), EntityOperation.SAVE, new DefaultEntityContext(actor))));
        assertEquals(List.of("data.save"), permissions);
    }

    @Test void cannotForgeTheParticipantScopeInAnEntityContext() {
        var forged = new DefaultApplicationParticipant("alice", "acme", "billing", "tenant-b", Map.of(), List.of());
        assertThrows(Exception.class, () -> await(inContext(() -> authorize(definition(), EntityOperation.FIND_BY_ID, new DefaultEntityContext(forged)))));
        assertTrue(permissions.isEmpty());
    }

    @Test void cannotAccessAnotherOrganizationOrApplication() {
        for (var definition : List.of(definition().setOrganizationId("other"), definition().setApplicationId("other"))) {
            assertThrows(Exception.class, () -> await(inContext(() -> authorize(definition, EntityOperation.FIND_BY_ID, new DefaultEntityContext(actor)))));
        }
        assertTrue(permissions.isEmpty());
    }

    @Test void tenantParticipantsCannotSelectAllTenants() {
        var context = new DefaultEntityContext(actor).setTenantSelection(List.of(EntityContext.ALL_TENANTS));
        assertThrows(Exception.class, () -> await(inContext(() -> authorize(definition(), EntityOperation.FIND_ALL, context))));
        assertTrue(permissions.isEmpty());
    }

    @Test void protectsIncludedReadFieldsAndAllWriteFields() throws Exception {
        var field = new RequirePermissionC3Decorator().setPermission("invoices.credit.read").setResourceType("entity_definition");
        var definition = definition().setDecoratedProperties(List.of(new DecoratedProperty("creditLimit", List.of(field))));
        await(inContext(() -> authorize(definition, EntityOperation.FIND_BY_ID, new DefaultEntityContext(actor, List.of("publicName")))));
        assertEquals(List.of("data.findById"), permissions);
        permissions.clear();
        await(inContext(() -> authorize(definition, EntityOperation.FIND_BY_ID, new DefaultEntityContext(actor, List.of("creditLimit")))));
        assertEquals(List.of("data.findById", "invoices.credit.read"), permissions);
        permissions.clear();
        await(inContext(() -> authorize(definition, EntityOperation.SAVE, new DefaultEntityContext(actor, List.of("publicName")))));
        assertEquals(List.of("data.save", "invoices.credit.read"), permissions);
    }

    @Test void legacyRestrictionsMustBeMigratedBeforeTheyCanBeReplaced() {
        var definition = definition();
        definition.getSchema().setDecorators(List.of(new PolicyDecorator()));
        assertThrows(Exception.class, () -> await(factory.createEntityDefinitionAuthorizationService(definition)));
    }

    @Test void namedQueriesRetainTheAuthenticatedActorAcrossAsynchronousOwnershipReads() throws Exception {
        var definition = definition();
        Promise<EntityDefinition> fetched = Promise.promise();
        var requested = new java.util.concurrent.CompletableFuture<Void>();
        when(entities.findById(definition.getId(), "acme")).thenAnswer(invocation -> {
            requested.complete(null);
            return fetched.future();
        });
        var result = inContext(() -> factory.createNamedQueryAuthorizationService(new FunctionDefinition().setName("findInvoices"), definition.toDescriptor())
                .compose(service -> service.authorize(NamedQueryOperation.EXECUTE, new DefaultEntityContext(actor))));
        requested.get(5, TimeUnit.SECONDS);
        java.util.concurrent.CompletableFuture.runAsync(() -> fetched.complete(definition)).get(5, TimeUnit.SECONDS);
        await(result);
        assertEquals(List.of("queries.execute"), permissions);
    }

    private EntityDefinition definition() {
        return new EntityDefinition().setId("acme.billing.invoice").setName("Invoice").setOrganizationId("acme")
                .setApplicationId("billing").setSchema(new ObjectC3Type());
    }

    private Future<Void> authorize(EntityDefinition definition, EntityOperation operation, EntityContext context) {
        return factory.createEntityDefinitionAuthorizationService(definition).compose(service -> service.authorize(operation, context));
    }

    private <T> Future<T> inContext(Supplier<Future<T>> work) {
        Promise<T> promise = Promise.promise();
        vertx.runOnContext(ignored -> {
            security.setParticipant(Vertx.currentContext(), actor);
            try { work.get().onComplete(promise); } catch (Exception failure) { promise.fail(failure); }
        });
        return promise.future();
    }

    private <T> T await(Future<T> result) throws Exception { return result.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS); }
}
