package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RequestAuthorizer;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies an application's authorization store from creation to deletion: the sample application's store runs
 * the model its published definitions imply, with a role for each definition's rows, brought in step by the
 * store's worker as a definition is published; an end user is refused the rows of a definition until granted on
 * its tenant, admitted after, through the authorizer the gateway calls, refused another definition's rows and
 * another tenant's, and refused again once the grant is revoked, while a grant made on the application reaches
 * every tenant; the organization's administrator manages the grants through {@code ApplicationAccessService},
 * which refuses a grant on anything but the application or a tenant and to anyone but the application's own
 * users; and deleting an application deletes its store with everything in it.
 */
@SpringBootTest
public class ApplicationStoreTests extends KinoticTestBase {

    private static final String ENTITIES_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.persistence.api.services.JsonEntitiesRepository";

    @Autowired
    private RequestAuthorizer authorizer;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationAccessService access;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private RelationshipService relationships;

    @Autowired
    private AuthzStoreService storeService;

    @Autowired
    private AuthzStoreRepository stores;

    @Autowired
    private TestDataService testData;

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    public void theStoreRunsTheModelItsPublishedDefinitionsImply() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        // a definition published now, so the worker is seen bringing the store in step with it
        EntityDefinition car = await(runAsOrganization(() -> testData.createCarEntityDefinitionIfNotExists("Store" + suffix()))).getLeft();
        String personType = DomainUtil.entityTypeOf(person.getId());
        String carType = DomainUtil.entityTypeOf(car.getId());

        assertTrue(awaitUntil(() -> roleIds().contains(AuthzUtil.roleId(carType, AuthzUtil.ADMIN))),
                   "the store never ran a model carrying the definition published");
        AuthzStore record = await(stores.findById(TEST_APP_ID));
        assertEquals(TEST_ORG_ID, record.getOrganizationId());
        assertEquals(TEST_APP_ID, record.getApplicationId());
        assertTrue(awaitUntil(() -> await(stores.findById(TEST_APP_ID)).getState().isReconciled()), "the record never reconciled");

        Set<String> roles = roleIds();
        assertTrue(roles.containsAll(List.of(AuthzUtil.roleId(personType, AuthzUtil.VIEWER), AuthzUtil.roleId(personType, AuthzUtil.EDITOR),
                                             AuthzUtil.roleId(personType, AuthzUtil.ADMIN), AuthzUtil.roleId(carType, AuthzUtil.ADMIN),
                                             AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.ADMIN), AuthzUtil.roleId(AuthzUtil.APPLICATION_TYPE, AuthzUtil.ADMIN))),
                   roles.toString());
        // a shared definition's rows sit in the tenant, so the tenant's admin holds everything of both
        RoleDefinition tenantAdmin = role(AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.ADMIN));
        assertTrue(tenantAdmin.builtIn());
        assertTrue(tenantAdmin.permissions().containsAll(List.of(AuthzUtil.permissionName(personType, AuthzUtil.CAN_DELETE),
                                                                 AuthzUtil.permissionName(carType, AuthzUtil.CAN_DELETE))),
                   tenantAdmin.permissions().toString());
    }

    @Test
    public void anEndUserIsAdmittedToItsTenantsRowsByAGrantAndRefusedWithout() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        EntityDefinition car = await(runAsOrganization(() -> testData.createCarEntityDefinitionIfNotExists(null))).getLeft();
        String personType = DomainUtil.entityTypeOf(person.getId());
        String carType = DomainUtil.entityTypeOf(car.getId());
        assertTrue(awaitUntil(() -> roleIds().contains(AuthzUtil.roleId(carType, AuthzUtil.ADMIN))), "the store never ran the definitions' model");
        String tenantId = "tenant-" + suffix();
        UserParticipantIdentity bob = endUser(TEST_APP_ID, tenantId);
        Participant caller = applicationParticipant(tenantId, bob.getId());
        Subject subject = new Subject(SubjectKind.USER, bob.getId());
        Resource tenant = new Resource(AuthzUtil.TENANT_TYPE, tenantId);
        List<Object> readRow = List.of(person.getId(), "row-1");
        // the contract carries the arguments a client sends; the participant is stamped on by the gateway
        assertRefused("findById", caller, readRow, AuthzUtil.permissionName(personType, "can_read") + " on tenant:" + tenantId);
        assertRefused("count", caller, List.of(person.getId()), AuthzUtil.permissionName(personType, "can_search"));

        Grant grant = await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(personType, AuthzUtil.EDITOR), tenant)));

        assertTrue(awaitUntil(() -> admitted("findById", caller, readRow)), "the editor was never admitted to the rows");
        authorize("count", caller, List.of(person.getId()));
        authorize("bulkUpdate", caller, List.of(person.getId(), List.of(Map.of("id", "row-1"))));
        // an editor neither deletes nor reads another definition's rows, and another tenant's user holds nothing
        assertRefused("deleteById", caller, readRow, AuthzUtil.permissionName(personType, "can_delete"));
        assertRefused("findById", caller, List.of(car.getId(), "row-1"), AuthzUtil.permissionName(carType, "can_read"));
        String otherTenant = "tenant-" + suffix();
        UserParticipantIdentity alice = endUser(TEST_APP_ID, otherTenant);
        Participant other = applicationParticipant(otherTenant, alice.getId());
        assertRefused("findById", other, readRow, "tenant:" + otherTenant);

        // the administrator sees the grant where it was made and what it explains
        List<Grant> grants = await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, tenant)));
        assertEquals(List.of(grant), grants);
        AccessExplanation explained = await(runAsOrganization(() -> access.explain(TEST_APP_ID, subject, AuthzUtil.permissionName(personType, "can_read"), tenant)));
        assertTrue(explained.allowed());
        assertEquals(List.of(grant), explained.through());
        assertFalse(await(runAsOrganization(() -> access.explain(TEST_APP_ID, subject, AuthzUtil.permissionName(carType, "can_read"), tenant))).allowed());

        // a grant on the application reaches every tenant, including one no grant was made on
        Grant everywhere = await(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectKind.USER, alice.getId()),
                                                                      AuthzUtil.roleId(personType, AuthzUtil.VIEWER),
                                                                      new Resource(AuthzUtil.APPLICATION_TYPE, TEST_APP_ID))));
        assertTrue(awaitUntil(() -> admitted("findById", other, readRow)), "the viewer granted on the application was never admitted");
        assertRefused("bulkUpdate", other, List.of(person.getId(), List.of()), AuthzUtil.permissionName(personType, "can_edit"));
        assertEquals(List.of(everywhere), await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, new Resource(AuthzUtil.TENANT_TYPE, otherTenant)))));

        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, tenant, grant.id())));
        assertTrue(awaitUntil(() -> !admitted("findById", caller, readRow)), "the revoked editor was still admitted");
        assertTrue(await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, tenant))).isEmpty());
        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, new Resource(AuthzUtil.APPLICATION_TYPE, TEST_APP_ID), everywhere.id())));
    }

    @Test
    public void aGrantIsMadeOnTheApplicationOrATenantToOneOfItsOwnUsers() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        String editor = AuthzUtil.roleId(DomainUtil.entityTypeOf(person.getId()), AuthzUtil.EDITOR);
        assertTrue(awaitUntil(() -> roleIds().contains(editor)), "the store never ran the definition's model");
        UserParticipantIdentity bob = endUser(TEST_APP_ID, "tenant-" + suffix());
        Subject subject = new Subject(SubjectKind.USER, bob.getId());
        Resource tenant = new Resource(AuthzUtil.TENANT_TYPE, bob.getTenantId());

        // a grant on a project, which the application's store knows nothing of
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant(TEST_APP_ID, subject, editor, new Resource("project", "p"))));
        // a grant to the organization's member, who is not one of the application's users
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> access.grant(TEST_APP_ID, new Subject(SubjectKind.USER, TEST_ORGANIZATION_PARTICIPANT.getId()), editor, tenant)));
        // a grant of a role the store does not define
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant(TEST_APP_ID, subject, "project.editor", tenant)));
        // a grant in an application of another organization, or of none
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant("no-such-application", subject, editor, tenant)));
    }

    @Test
    public void deletingAnApplicationDeletesItsStoreWithEverythingInIt() throws Exception {
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Store " + suffix(), "lifecycle", null)));
        String store = application.getId();

        // created with its store, running the kernel model, and the record the worker keeps in step
        AuthzStore record = await(stores.findById(store));
        assertNotNull(record);
        assertEquals(TEST_ORG_ID, record.getOrganizationId());
        assertNotNull(await(storeService.modelId(store)));
        UserParticipantIdentity bob = endUser(store, "tenant-" + suffix());
        String me = AuthzUtil.object(AuthzUtil.USER_TYPE, bob.getId());
        assertTrue(await(relationships.holds(store, new RelationshipTuple(me, AuthzUtil.END_USER_RELATION, AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, store)))));
        assertTrue(await(relationships.holds(store, new RelationshipTuple(me, AuthzUtil.MEMBER_RELATION, AuthzUtil.object(AuthzUtil.TENANT_TYPE, bob.getTenantId())))));
        assertTrue(await(relationships.holds(AuthzStoreService.PLATFORM, new RelationshipTuple(me, AuthzUtil.END_USER_RELATION, AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, store)))));

        await(runAsOrganization(() -> applicationService.deleteById(store)));

        assertNull(await(stores.findById(store)));
        assertThrows(ExecutionException.class, () -> await(storeService.modelId(store)));
        assertThrows(ExecutionException.class, () -> await(relationships.read(store, AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, store))));
    }

    private Set<String> roleIds() throws Exception {
        Set<String> ret = new HashSet<>();
        for (RoleDefinition role : await(runAsOrganization(() -> access.findRoles(TEST_APP_ID)))) {
            ret.add(role.id());
        }
        return ret;
    }

    private RoleDefinition role(String id) throws Exception {
        return await(runAsOrganization(() -> access.findRoles(TEST_APP_ID))).stream()
                                                                             .filter(role -> id.equals(role.id()))
                                                                             .findFirst()
                                                                             .orElseThrow();
    }

    private UserParticipantIdentity endUser(String applicationId, String tenantId) throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("end-user-" + suffix() + "@kinotic.test");
        user.setDisplayName("End User");
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(applicationId);
        user.setTenantId(tenantId);
        return await(identityService.createUser(user, "End-User-1"));
    }

    private void authorize(String function, Participant caller, Object arguments) throws Exception {
        await(authorizer.authorize(cri(function), caller, EventConstants.CONTENT_TYPE_JSON,
                                   jsonMapper.writeValueAsString(arguments).getBytes(StandardCharsets.UTF_8)));
    }

    private boolean admitted(String function, Participant caller, Object arguments) throws Exception {
        boolean ret;
        try {
            authorize(function, caller, arguments);
            ret = true;
        } catch (ExecutionException refused) {
            assertInstanceOf(AuthorizationException.class, refused.getCause());
            ret = false;
        }
        return ret;
    }

    private void assertRefused(String function, Participant caller, Object arguments, String naming) throws Exception {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> authorize(function, caller, arguments));
        AuthorizationException refused = assertInstanceOf(AuthorizationException.class, failure.getCause());
        assertTrue(refused.getMessage().contains(naming), refused.getMessage());
    }

    private Throwable failure(java.util.function.Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAsOrganization(call::get))).getCause();
    }

    private CRI cri(String function) throws Exception {
        ServiceDirectoryEntry entry = await(serviceDirectory.findEntry(ENTITIES_SERVICE));
        assertNotNull(entry, "the directory holds no contract for " + ENTITIES_SERVICE);
        return CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME + "://" + TEST_APP_ID + "@" + ENTITIES_SERVICE + "/" + function + "#" + entry.getVersion());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
