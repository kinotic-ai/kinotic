package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.AuthzCheckC3Decorator;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Pins how a request is resolved against its function's contract: the object read from a positional or a
 * named body, at the parameter's position or by its name and down a property path; the caller's scope for a
 * scope reference, at the caller's own level when the check names one below it, and on the platform for a
 * system participant, and on its own application's store for an application participant, on its tenant, or on
 * the application when it has none; a definition's rows checked on the definition a request names, within the
 * caller's tenant with the object's two edges supplied, on the definition itself for a user without a tenant, and
 * in the platform's store for an organization member; an application or a project named within the caller's
 * organization, which a system participant cannot name; a
 * delegate checked as its owner; a function its contract marks unchecked passing without the engine; and the
 * refusals: a denied check, a request naming no object, a body no id can be read from, and the requests no
 * contract covers, of a service the directory holds no definition for, a function the definition leaves out or
 * marks neither way, and a node running no directory.
 */
class DefaultRequestAuthorizerTest {

    private static final String SERVICE = "management-api~org.kinotic.management.api.services.ProjectService";
    private static final String ENTITIES = "app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository";
    private static final String MODEL_ID = "model-1";
    private static final String CRM_MODEL_ID = "crm-model-1";

    private ServiceDirectory directory;
    private RelationshipService relationships;
    private DefaultRequestAuthorizer authorizer;
    private ServiceDirectoryEntry entry;
    private Vertx vertx;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        directory = mock(ServiceDirectory.class);
        ObjectProvider<ServiceDirectory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(directory);
        AuthzStoreService stores = mock(AuthzStoreService.class);
        when(stores.modelId(PLATFORM)).thenReturn(Future.succeededFuture(MODEL_ID));
        when(stores.modelId("acme.crm")).thenReturn(Future.succeededFuture(CRM_MODEL_ID));
        relationships = mock(RelationshipService.class);
        when(relationships.check(eq(PLATFORM), eq(MODEL_ID), any(), any(), any())).thenReturn(Future.succeededFuture(true));
        when(relationships.check(eq("acme.crm"), eq(CRM_MODEL_ID), any(), any(), any())).thenReturn(Future.succeededFuture(true));
        vertx = Vertx.vertx();
        authorizer = new DefaultRequestAuthorizer(provider, stores, relationships, JsonMapper.builder().build(), vertx);
        authorizer.listenForContractChanges();

        ServiceDefinition service = new ServiceDefinition().setNamespace("org.kinotic.management.api.services").setName("ProjectService");
        service.addFunction(function("save", check("project", "{entity.id}", "project", "can_edit", false), "entity"));
        // the Participant parameter of deploy is not in the contract, so projectId is the body's first element
        service.addFunction(function("deploy", check("project", "{projectId}", "project", "can_deploy", true), "projectId"));
        service.addFunction(function("findMembers", check("organization", "{@organizationId}", "organization", "can_view_members", false)));
        service.addFunction(function("count", check("application", "{@applicationId}", "project", "can_view", false)));
        service.addFunction(function("listAccessible", unchecked(), "type", "permission"));
        // a function the contract marks neither way
        service.addFunction(function("describe", null, "projectId"));
        entry = new ServiceDirectoryEntry().setId(SERVICE).setServiceDefinition(service);
        when(directory.findEntry(SERVICE)).thenReturn(Future.succeededFuture(entry));

        // the rows of an entity definition, checked on the definition each request names
        ServiceDefinition entities = new ServiceDefinition().setNamespace("org.kinotic.persistence.api.services").setName("JsonEntitiesRepository");
        entities.addFunction(function("findById", check("entity_definition", "{entityDefinitionId}", "entity_definition", "can_read", false), "entityDefinitionId", "id"));
        entities.addFunction(function("save", check("entity_definition", "{entityDefinitionId}", "entity_definition", "can_create", false), "entityDefinitionId", "entity"));
        when(directory.findEntry(ENTITIES)).thenReturn(Future.succeededFuture(new ServiceDirectoryEntry().setId(ENTITIES).setServiceDefinition(entities)));
    }

    @AfterEach
    void tearDown() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void aContractAnnouncedWrittenIsReadAgain() throws Exception {
        authorize("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-a\"}]");
        verify(directory, times(1)).findEntry(SERVICE);
        verify(relationships, times(1)).check(any(), any(), any(), any(), any());

        // the contract written again serves save unchecked
        ServiceDefinition changed = new ServiceDefinition().setNamespace("org.kinotic.management.api.services").setName("ProjectService");
        changed.addFunction(function("save", unchecked(), "entity"));
        when(directory.findEntry(SERVICE)).thenReturn(Future.succeededFuture(new ServiceDirectoryEntry().setId(SERVICE).setServiceDefinition(changed)));
        vertx.eventBus().publish(ServiceDirectory.CONTRACT_CHANGED_ADDRESS, SERVICE);

        // the announcement lands on the event loop; from then on a request reads the contract again and asks nothing
        assertTrue(awaitUntil(() -> {
            int checks = mockingDetails(relationships).getInvocations().size();
            authorize("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-a\"}]");
            return mockingDetails(relationships).getInvocations().size() == checks
                    && mockingDetails(directory).getInvocations().size() == 2;
        }), "the contract announced written was never read again");
    }

    @Test
    void aPositionalBodyNamesTheObjectAtTheParametersPosition() throws Exception {
        authorize("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"name\":\"A\",\"id\":\"proj-a\"}]");

        RelationshipTuple checked = checked(Consistency.MINIMIZE_LATENCY);
        assertEquals(new RelationshipTuple("user:sally", "project_can_edit", "project:acme.proj-a"), checked);
    }

    @Test
    void aNamedBodyNamesTheObjectByTheParametersName() throws Exception {
        authorize("save", sally(), EventConstants.CONTENT_TYPE_NAMED_JSON,
                  "{\"other\":{\"id\":\"not-this\"},\"entity\":{\"tags\":[1,{\"id\":\"nor-this\"}],\"id\":\"proj-a\"}}");

        assertEquals("project:acme.proj-a", checked(Consistency.MINIMIZE_LATENCY).object());
    }

    @Test
    void aConsistentCheckAsksTheEngineForAConsistentAnswer() throws Exception {
        authorize("deploy", sally(), EventConstants.CONTENT_TYPE_JSON, "[\"proj-a\"]");

        assertEquals(new RelationshipTuple("user:sally", "project_can_deploy", "project:acme.proj-a"), checked(Consistency.HIGHER_CONSISTENCY));
    }

    @Test
    void aScopeReferenceReadsTheCallersScope() throws Exception {
        authorize("findMembers", sally(), EventConstants.CONTENT_TYPE_JSON, "[]");

        assertEquals(new RelationshipTuple("user:sally", "organization_can_view_members", "organization:acme"), checked(Consistency.MINIMIZE_LATENCY));
    }

    @Test
    void aCheckOnAScopeLevelAboveTheCallersIsMadeOnTheCallersOwn() throws Exception {
        // an organization member has no application in its scope, so the application's projects are its organization's
        authorize("count", sally(), EventConstants.CONTENT_TYPE_JSON, "[]");

        assertEquals(new RelationshipTuple("user:sally", "project_can_view", "organization:acme"), checked(Consistency.MINIMIZE_LATENCY));
    }

    @Test
    void aDelegateIsCheckedAsItsOwner() throws Exception {
        Participant delegate = DefaultOrganizationParticipant.builder()
                                                             .id("cli-session")
                                                             .organizationId("acme")
                                                             .metadata(Map.of(DomainUtil.ON_BEHALF_OF_METADATA_KEY, "sally"))
                                                             .roles(List.of())
                                                             .build();

        authorize("save", delegate, EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-a\"}]");

        assertEquals("user:sally", checked(Consistency.MINIMIZE_LATENCY).user());
    }

    @Test
    void aDeniedCheckRefusesTheRequest() {
        when(relationships.check(eq(PLATFORM), eq(MODEL_ID), any(), any(), any())).thenReturn(Future.succeededFuture(false));

        AuthorizationException refused = refused("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-b\"}]");

        assertTrue(refused.getMessage().contains("project_can_edit"), refused.getMessage());
        assertTrue(refused.getMessage().contains("project:acme.proj-b"), refused.getMessage());
    }

    @Test
    void aRequestNamingNoObjectIsRefusedWithoutTheEngine() {
        AuthorizationException refused = refused("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"name\":\"A\"}]");

        assertTrue(refused.getMessage().contains("entity.id"), refused.getMessage());
        verify(relationships, never()).check(any(), any(), any(), any(), any());
    }

    @Test
    void aBodyNoIdCanBeReadFromIsRefused() {
        assertTrue(refused("save", sally(), "application/octet-stream", "[{\"id\":\"proj-a\"}]").getMessage().contains("octet-stream"));
        assertTrue(refused("save", sally(), EventConstants.CONTENT_TYPE_JSON, "").getMessage().contains("no body"));
        verify(relationships, never()).check(any(), any(), any(), any(), any());
    }

    @Test
    void anUncheckedFunctionPassesWithoutTheEngine() throws Exception {
        authorize("listAccessible", sally(), EventConstants.CONTENT_TYPE_JSON, "[\"project\",\"can_view\"]");

        verify(relationships, never()).check(any(), any(), any(), any(), any());
    }

    @Test
    void aServiceWithoutAContractIsRefused() {
        when(directory.findEntry("app.acme.crm~OrderService")).thenReturn(Future.succeededFuture(null));

        ExecutionException failure = assertThrows(ExecutionException.class,
                                                  () -> authorizer.authorize(CRI.create("srv://app.acme.crm~OrderService/create#1.0.0"), sally(),
                                                                             EventConstants.CONTENT_TYPE_JSON, bytes("[{}]"))
                                                                  .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));

        AuthorizationException refused = assertInstanceOf(AuthorizationException.class, failure.getCause());
        assertTrue(refused.getMessage().contains("No contract covers create"), refused.getMessage());
        verify(relationships, never()).check(any(), any(), any(), any(), any());
    }

    @Test
    void aFunctionTheContractLeavesOutIsRefused() {
        AuthorizationException refused = refused("render", sally(), EventConstants.CONTENT_TYPE_JSON, "[]");

        assertTrue(refused.getMessage().contains("No contract covers render"), refused.getMessage());
        verify(relationships, never()).check(any(), any(), any(), any(), any());
    }

    @Test
    void aFunctionMarkedNeitherCheckedNorUncheckedIsRefused() {
        AuthorizationException refused = refused("describe", sally(), EventConstants.CONTENT_TYPE_JSON, "[\"proj-a\"]");

        assertTrue(refused.getMessage().contains("No contract covers describe"), refused.getMessage());
        verify(relationships, never()).check(any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNodeWithoutADirectoryRefusesEveryRequest() {
        ObjectProvider<ServiceDirectory> none = mock(ObjectProvider.class);
        when(none.getIfAvailable()).thenReturn(null);
        DefaultRequestAuthorizer alone = new DefaultRequestAuthorizer(none, mock(AuthzStoreService.class), relationships, JsonMapper.builder().build(), vertx);

        ExecutionException failure = assertThrows(ExecutionException.class,
                                                  () -> alone.authorize(cri("listAccessible"), sally(), EventConstants.CONTENT_TYPE_JSON, bytes("[]"))
                                                             .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));

        AuthorizationException refused = assertInstanceOf(AuthorizationException.class, failure.getCause());
        assertTrue(refused.getMessage().contains("No service directory"), refused.getMessage());
    }

    @Test
    void aTenantsUserIsCheckedOnTheDefinitionWithinItsTenantWithItsEdgesSupplied() throws Exception {
        authorizer.authorize(CRI.create("srv://crm@" + ENTITIES + "/findById#1.0.0"), bob("t1"),
                             EventConstants.CONTENT_TYPE_JSON, bytes("[\"acme.crm.person\",\"row-1\"]"))
                  .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        ArgumentCaptor<RelationshipTuple> tuple = ArgumentCaptor.captor();
        ArgumentCaptor<List<RelationshipTuple>> edges = ArgumentCaptor.captor();
        verify(relationships).check(eq("acme.crm"), eq(CRM_MODEL_ID), tuple.capture(), eq(Consistency.MINIMIZE_LATENCY), edges.capture());
        assertEquals(new RelationshipTuple("user:bob", "entity_definition_can_read", "tenant_definition:acme.crm.person@t1"), tuple.getValue());
        // the object no store holds tuples for comes with its two edges, the definition's and the tenant's
        assertEquals(List.of(new RelationshipTuple("entity_definition:acme.crm.person", "definition", "tenant_definition:acme.crm.person@t1"),
                             new RelationshipTuple("tenant:t1", "tenant", "tenant_definition:acme.crm.person@t1")),
                     edges.getValue());
        verify(relationships, never()).check(eq(PLATFORM), any(), any(), any(), any());
    }

    @Test
    void aUserWithoutATenantIsCheckedOnTheDefinitionItself() throws Exception {
        authorizer.authorize(CRI.create("srv://crm@" + ENTITIES + "/findById#1.0.0"), bob(null),
                             EventConstants.CONTENT_TYPE_JSON, bytes("[\"acme.crm.person\",\"row-1\"]"))
                  .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(new RelationshipTuple("user:bob", "entity_definition_can_read", "entity_definition:acme.crm.person"),
                     checked("acme.crm", CRM_MODEL_ID, Consistency.MINIMIZE_LATENCY));
    }

    @Test
    void anOrganizationMemberIsCheckedOnTheDefinitionInThePlatformsStore() throws Exception {
        authorizer.authorize(CRI.create("srv://crm@" + ENTITIES + "/save#1.0.0"), sally(),
                             EventConstants.CONTENT_TYPE_JSON, bytes("[\"acme.crm.person\",{}]"))
                  .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(new RelationshipTuple("user:sally", "entity_definition_can_create", "entity_definition:acme.crm.person"),
                     checked(Consistency.MINIMIZE_LATENCY));
        verify(relationships, never()).check(eq("acme.crm"), any(), any(), any(), any());
    }

    @Test
    void aValueThatCanNameNoResourceIsRefusedBeforeTheEngineIsAsked() {
        ExecutionException failure = assertThrows(ExecutionException.class,
                                                  () -> authorizer.authorize(CRI.create("srv://crm@" + ENTITIES + "/findById#1.0.0"), bob("t1"),
                                                                             EventConstants.CONTENT_TYPE_JSON, bytes("[\"acme.crm.person#definition\",\"row-1\"]"))
                                                                  .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));

        AuthorizationException refused = assertInstanceOf(AuthorizationException.class, failure.getCause());
        assertTrue(refused.getMessage().contains("names no resource"), refused.getMessage());
        verify(relationships, never()).check(any(), any(), any(), any(), any());
    }

    @Test
    void anApplicationParticipantIsRefusedWhatItsStoreDenies() {
        when(relationships.check(eq("acme.crm"), eq(CRM_MODEL_ID), any(), any(), any())).thenReturn(Future.succeededFuture(false));

        ExecutionException failure = assertThrows(ExecutionException.class,
                                                  () -> authorizer.authorize(CRI.create("srv://crm@" + ENTITIES + "/findById#1.0.0"), bob("t1"),
                                                                             EventConstants.CONTENT_TYPE_JSON, bytes("[\"acme.crm.person\",\"row-1\"]"))
                                                                  .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));

        AuthorizationException refused = assertInstanceOf(AuthorizationException.class, failure.getCause());
        assertTrue(refused.getMessage().contains("entity_definition_can_read on tenant_definition:acme.crm.person@t1"), refused.getMessage());
    }

    @Test
    void aSystemParticipantIsCheckedOnThePlatformAndNamesNoProject() throws Exception {
        Participant operator = DefaultSystemParticipant.builder().id("ops").metadata(Map.of()).roles(List.of()).build();

        // a project is named within an organization, which an operator has none of
        AuthorizationException refused = refused("save", operator, EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-b\"}]");
        assertTrue(refused.getMessage().contains("project proj-b"), refused.getMessage());

        // a check on the caller's organization is made on the platform
        authorize("findMembers", operator, EventConstants.CONTENT_TYPE_JSON, "[]");
        assertEquals(new RelationshipTuple("user:ops", "organization_can_view_members", "platform:kinotic"), checked(Consistency.MINIMIZE_LATENCY));
    }

    @Test
    void theContractIsReadOnceAndKept() throws Exception {
        authorize("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-a\"}]");
        authorize("deploy", sally(), EventConstants.CONTENT_TYPE_JSON, "[\"proj-a\"]");

        verify(directory, times(1)).findEntry(SERVICE);
    }

    @Test
    void anEngineFailureFailsTheRequestAsItself() {
        IllegalStateException down = new IllegalStateException("engine unreachable");
        when(relationships.check(eq(PLATFORM), eq(MODEL_ID), any(), any(), any())).thenReturn(Future.failedFuture(down));

        ExecutionException failure = assertThrows(ExecutionException.class,
                                                  () -> authorize("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-a\"}]"));

        assertEquals(down, failure.getCause());
    }

    private void authorize(String function, Participant participant, String contentType, String body) throws Exception {
        authorizer.authorize(cri(function), participant, contentType, bytes(body))
                  .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private AuthorizationException refused(String function, Participant participant, String contentType, String body) {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> authorize(function, participant, contentType, body));
        return assertInstanceOf(AuthorizationException.class, failure.getCause());
    }

    // polls every 20 ms for up to 5 s
    private static boolean awaitUntil(Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.call() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return condition.call();
    }

    private RelationshipTuple checked(Consistency consistency) {
        return checked(PLATFORM, MODEL_ID, consistency);
    }

    private RelationshipTuple checked(String store, String modelId, Consistency consistency) {
        ArgumentCaptor<RelationshipTuple> tuple = ArgumentCaptor.forClass(RelationshipTuple.class);
        verify(relationships).check(eq(store), eq(modelId), tuple.capture(), eq(consistency), eq(List.of()));
        return tuple.getValue();
    }

    private static CRI cri(String function) {
        return CRI.create("srv://acme@" + SERVICE + "/" + function + "#1.0.0");
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static Participant sally() {
        return DefaultOrganizationParticipant.builder().id("sally").organizationId("acme").metadata(Map.of()).roles(List.of()).build();
    }

    // an end user of the application crm, in the given tenant or in none
    private static Participant bob(String tenantId) {
        return DefaultApplicationParticipant.builder().id("bob").organizationId("acme").applicationId("crm")
                                            .tenantId(tenantId).metadata(Map.of()).roles(List.of()).build();
    }

    private static FunctionDefinition function(String name, AuthzCheckC3Decorator check, String... parameters) {
        FunctionDefinition ret = new FunctionDefinition().setName(name);
        for (String parameter : parameters) {
            ret.addParameter(parameter, new StringC3Type());
        }
        if (check != null) {
            ret.setDecorators(List.of(check));
        }
        return ret;
    }

    private static AuthzCheckC3Decorator unchecked() {
        return new AuthzCheckC3Decorator().setUnchecked(true);
    }

    private static AuthzCheckC3Decorator check(String resource, String resourceId, String permissionResource, String permission, boolean consistent) {
        return new AuthzCheckC3Decorator().setResource(resource)
                                          .setResourceId(resourceId)
                                          .setPermissionResource(permissionResource)
                                          .setPermission(permission)
                                          .setImplies(List.of())
                                          .setConsistent(consistent);
    }
}
