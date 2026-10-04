package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how a request is resolved against its function's contract: the object read from a positional or a
 * named body, at the parameter's position or by its name and down a property path; the caller's scope for a
 * scope reference, at the caller's own level when the check names one below it, and on the platform for a
 * system participant; a delegate checked as its owner; a zone-only function, a service with no entry and an
 * application participant passing without the engine; and the refusals: a denied check, a request naming no
 * object, a body no id can be read from.
 */
class DefaultRequestAuthorizerTest {

    private static final String SERVICE = "management-api~org.kinotic.management.api.services.ProjectService";
    private static final String MODEL_ID = "model-1";

    private ServiceDirectory directory;
    private RelationshipService relationships;
    private DefaultRequestAuthorizer authorizer;
    private ServiceDirectoryEntry entry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        directory = mock(ServiceDirectory.class);
        ObjectProvider<ServiceDirectory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(directory);
        AuthzStoreService stores = mock(AuthzStoreService.class);
        when(stores.platformModelId()).thenReturn(Future.succeededFuture(MODEL_ID));
        relationships = mock(RelationshipService.class);
        when(relationships.check(eq(PLATFORM), eq(MODEL_ID), any(), any())).thenReturn(Future.succeededFuture(true));
        authorizer = new DefaultRequestAuthorizer(provider, stores, relationships, JsonMapper.builder().build());

        ServiceDefinition service = new ServiceDefinition().setNamespace("org.kinotic.management.api.services").setName("ProjectService");
        service.addFunction(function("save", check("project", "{entity.id}", "project", "can_edit", false), "entity"));
        // the Participant parameter of deploy is not in the contract, so projectId is the body's first element
        service.addFunction(function("deploy", check("project", "{projectId}", "project", "can_deploy", true), "projectId"));
        service.addFunction(function("findMembers", check("organization", "{@organizationId}", "organization", "can_view_members", false)));
        service.addFunction(function("count", check("application", "{@applicationId}", "project", "can_view", false)));
        service.addFunction(function("listAccessible", null, "type", "permission"));
        entry = new ServiceDirectoryEntry().setId(SERVICE).setServiceDefinition(service);
        when(directory.findEntry(SERVICE)).thenReturn(Future.succeededFuture(entry));
    }

    @Test
    void aPositionalBodyNamesTheObjectAtTheParametersPosition() throws Exception {
        authorize("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"name\":\"A\",\"id\":\"proj-a\"}]");

        RelationshipTuple checked = checked(Consistency.MINIMIZE_LATENCY);
        assertEquals(new RelationshipTuple("user:sally", "project_can_edit", "project:proj-a"), checked);
    }

    @Test
    void aNamedBodyNamesTheObjectByTheParametersName() throws Exception {
        authorize("save", sally(), EventConstants.CONTENT_TYPE_NAMED_JSON,
                  "{\"other\":{\"id\":\"not-this\"},\"entity\":{\"tags\":[1,{\"id\":\"nor-this\"}],\"id\":\"proj-a\"}}");

        assertEquals("project:proj-a", checked(Consistency.MINIMIZE_LATENCY).object());
    }

    @Test
    void aConsistentCheckAsksTheEngineForAConsistentAnswer() throws Exception {
        authorize("deploy", sally(), EventConstants.CONTENT_TYPE_JSON, "[\"proj-a\"]");

        assertEquals(new RelationshipTuple("user:sally", "project_can_deploy", "project:proj-a"), checked(Consistency.HIGHER_CONSISTENCY));
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
        when(relationships.check(eq(PLATFORM), eq(MODEL_ID), any(), any())).thenReturn(Future.succeededFuture(false));

        AuthorizationException refused = refused("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-b\"}]");

        assertTrue(refused.getMessage().contains("project_can_edit"), refused.getMessage());
        assertTrue(refused.getMessage().contains("project:proj-b"), refused.getMessage());
    }

    @Test
    void aRequestNamingNoObjectIsRefusedWithoutTheEngine() {
        AuthorizationException refused = refused("save", sally(), EventConstants.CONTENT_TYPE_JSON, "[{\"name\":\"A\"}]");

        assertTrue(refused.getMessage().contains("entity.id"), refused.getMessage());
        verify(relationships, never()).check(any(), any(), any(), any());
    }

    @Test
    void aBodyNoIdCanBeReadFromIsRefused() {
        assertTrue(refused("save", sally(), "application/octet-stream", "[{\"id\":\"proj-a\"}]").getMessage().contains("octet-stream"));
        assertTrue(refused("save", sally(), EventConstants.CONTENT_TYPE_JSON, "").getMessage().contains("no body"));
        verify(relationships, never()).check(any(), any(), any(), any());
    }

    @Test
    void aZoneOnlyFunctionPassesWithoutTheEngine() throws Exception {
        authorize("listAccessible", sally(), EventConstants.CONTENT_TYPE_JSON, "[\"project\",\"can_view\"]");

        verify(relationships, never()).check(any(), any(), any(), any());
    }

    @Test
    void aServiceWithoutAnEntryPassesOnTheZone() throws Exception {
        when(directory.findEntry("app.acme.crm~OrderService")).thenReturn(Future.succeededFuture(null));

        authorizer.authorize(CRI.create("srv://app.acme.crm~OrderService/create#1.0.0"), sally(),
                             EventConstants.CONTENT_TYPE_JSON, bytes("[{}]"))
                  .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

        verify(relationships, never()).check(any(), any(), any(), any());
    }

    @Test
    void anApplicationParticipantPassesOnItsZone() throws Exception {
        Participant appUser = DefaultApplicationParticipant.builder().id("bob").organizationId("acme").applicationId("crm")
                                                           .tenantId("t1").metadata(Map.of()).roles(List.of()).build();

        authorize("save", appUser, EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-b\"}]");

        verify(relationships, never()).check(any(), any(), any(), any());
        verify(directory, never()).findEntry(any());
    }

    @Test
    void aSystemParticipantIsCheckedAndItsScopeIsThePlatform() throws Exception {
        Participant operator = DefaultSystemParticipant.builder().id("ops").metadata(Map.of()).roles(List.of()).build();

        authorize("save", operator, EventConstants.CONTENT_TYPE_JSON, "[{\"id\":\"proj-b\"}]");
        assertEquals(new RelationshipTuple("user:ops", "project_can_edit", "project:proj-b"), checked(Consistency.MINIMIZE_LATENCY));

        // a check on the caller's organization, which an operator has none of, is made on the platform
        authorize("findMembers", operator, EventConstants.CONTENT_TYPE_JSON, "[]");
        ArgumentCaptor<RelationshipTuple> tuples = ArgumentCaptor.forClass(RelationshipTuple.class);
        verify(relationships, times(2)).check(eq(PLATFORM), eq(MODEL_ID), tuples.capture(), eq(Consistency.MINIMIZE_LATENCY));
        assertEquals(new RelationshipTuple("user:ops", "organization_can_view_members", "platform:kinotic"), tuples.getValue());
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
        when(relationships.check(eq(PLATFORM), eq(MODEL_ID), any(), any())).thenReturn(Future.failedFuture(down));

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

    private RelationshipTuple checked(Consistency consistency) {
        ArgumentCaptor<RelationshipTuple> tuple = ArgumentCaptor.forClass(RelationshipTuple.class);
        verify(relationships).check(eq(PLATFORM), eq(MODEL_ID), tuple.capture(), eq(consistency));
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

    private static AuthzCheckC3Decorator check(String resource, String objectId, String permissionResource, String permission, boolean consistent) {
        return new AuthzCheckC3Decorator().setResource(resource)
                                          .setObjectId(objectId)
                                          .setPermissionResource(permissionResource)
                                          .setPermission(permission)
                                          .setImplies(List.of())
                                          .setConsistent(consistent);
    }
}
