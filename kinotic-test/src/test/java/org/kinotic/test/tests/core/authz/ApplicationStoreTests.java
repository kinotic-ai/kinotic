package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.TenantService;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies an application's authorization store from creation to deletion: the sample application's store runs
 * the fixed model, with the roles of a definition's rows, and holds each definition the application creates;
 * an end user is refused the rows of a definition until granted on the definition within its tenant, admitted
 * after, through the authorizer the gateway calls, refused another definition's rows and another tenant's, and
 * refused again once the grant is revoked, while a grant made on the definition reaches it in every tenant and
 * one made on the application reaches every definition; the organization's administrator manages the grants
 * through {@code ApplicationAccessService}, which refuses a grant on anything but the application, a tenant, a
 * definition the application holds or a definition within a tenant, and to anyone but the application's own
 * users; deleting an application deletes its store with everything in it; and an application created again under
 * the same id is placed in its store whatever model the store was left running.
 */
@SpringBootTest
public class ApplicationStoreTests extends KinoticTestBase {

    private static final String ENTITIES_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.persistence.api.services.JsonEntitiesRepository";
    private static final String ADMIN_ENTITIES_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.persistence.api.services.AdminJsonEntitiesRepository";

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
    private ServiceDirectory directory;

    @Autowired
    private AuthzModelGenerator generator;

    @Autowired
    private TenantService tenantService;

    @Test
    public void theStoreRunsTheFixedModelAndHoldsTheDefinitionsCreated() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        // a definition created now, so the store is seen taking it as it is created
        EntityDefinition car = await(runAsOrganization(() -> testData.createCarEntityDefinitionIfNotExists("Store" + suffix()))).getLeft();
        String store = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID);

        AuthzStore record = await(stores.findById(store));
        assertEquals(TEST_ORG_ID, record.getOrganizationId());
        assertEquals(TEST_APP_ID, record.getApplicationId());
        assertTrue(awaitUntil(() -> await(stores.findById(store)).getState().isReconciled()), "the record never reconciled");

        // the roles of a definition's rows are the model's, whatever definitions the application holds
        Set<String> roles = roleIds();
        assertTrue(roles.containsAll(List.of(AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER),
                                             AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.EDITOR),
                                             AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.ADMIN),
                                             AuthzUtil.roleId(AuthzConstants.TENANT_TYPE, AuthzConstants.ADMIN), AuthzUtil.roleId(AuthzConstants.APPLICATION_TYPE, AuthzConstants.ADMIN))),
                   roles.toString());
        // a shared definition's rows sit in the tenant, so the tenant's admin holds everything of them
        RoleDefinition tenantAdmin = role(AuthzUtil.roleId(AuthzConstants.TENANT_TYPE, AuthzConstants.ADMIN));
        assertTrue(tenantAdmin.builtIn());
        assertTrue(tenantAdmin.permissions().contains(AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_DELETE)),
                   tenantAdmin.permissions().toString());
        assertEquals(Set.of(AuthzConstants.APPLICATION_TYPE, AuthzConstants.TENANT_TYPE), tenantAdmin.grantableOn());
        // the application is placed in its own store, and each definition created is placed under it, which is what
        // lets a grant on the tenant reach the definition's rows there
        String application = AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, store);
        assertTrue(await(relationships.holds(store, new RelationshipTuple(AuthzConstants.EVERYONE, AuthzConstants.PLACED_RELATION, application))));
        assertTrue(await(relationships.holds(store, new RelationshipTuple(application, AuthzConstants.APPLICATION_TYPE, AuthzUtil.object(AuthzConstants.ENTITY_DEFINITION_TYPE, person.getId())))));
        assertTrue(await(relationships.holds(store, new RelationshipTuple(application, AuthzConstants.APPLICATION_TYPE, AuthzUtil.object(AuthzConstants.ENTITY_DEFINITION_TYPE, car.getId())))));
    }

    @Test
    public void anEndUserIsAdmittedToItsTenantsRowsByAGrantAndRefusedWithout() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        EntityDefinition car = await(runAsOrganization(() -> testData.createCarEntityDefinitionIfNotExists(null))).getLeft();
        String tenantId = "tenant-" + suffix();
        ApplicationParticipantIdentity bob = endUser(TEST_APP_ID, tenantId);
        Participant caller = applicationParticipant(tenantId, bob.getId());
        Subject subject = new Subject(SubjectType.USER, bob.getId());
        Resource personHere = new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(person.getId(), tenantId));
        Resource carHere = new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(car.getId(), tenantId));
        List<Object> readRow = List.of(person.getId(), "row-1");
        // the contract carries the arguments a client sends; the participant is stamped on by the gateway
        assertRefused(ENTITIES_SERVICE, "findById", caller, readRow, AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_READ)
                + " on " + AuthzUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE, personHere.id()));
        assertRefused(ENTITIES_SERVICE, "count", caller, List.of(person.getId()), AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_READ));

        Grant grant = await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.EDITOR), personHere)));

        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", caller, readRow)), "the editor was never admitted to the rows");
        authorize(ENTITIES_SERVICE, "count", caller, List.of(person.getId()));
        authorize(ENTITIES_SERVICE, "bulkUpdate", caller, List.of(person.getId(), List.of(Map.of("id", "row-1"))));
        // an editor neither deletes nor reads another definition's rows, and another tenant's user holds nothing
        assertRefused(ENTITIES_SERVICE, "deleteById", caller, readRow, AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_DELETE));
        // the admin repository acts on the rows of the tenants a call names, so it checks each function for the
        // permission the entities repository does on the definition itself, which a grant within the tenant does not reach
        List<Object> adminRow = List.of(person.getId(), Map.of("entityId", "row-1", "tenantId", tenantId));
        String personObject = AuthzUtil.object(AuthzConstants.ENTITY_DEFINITION_TYPE, person.getId());
        assertRefused(ADMIN_ENTITIES_SERVICE, "findById", caller, adminRow, AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_READ) + " on " + personObject);
        assertRefused(ADMIN_ENTITIES_SERVICE, "count", caller, List.of(person.getId(), List.of(tenantId)), personObject);
        assertRefused(ADMIN_ENTITIES_SERVICE, "deleteByQuery", caller, List.of(person.getId(), "*", List.of(tenantId)), AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_DELETE));
        assertRefused(ENTITIES_SERVICE, "findById", caller, List.of(car.getId(), "row-1"), AuthzUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE, carHere.id()));
        String otherTenant = "tenant-" + suffix();
        ApplicationParticipantIdentity alice = endUser(TEST_APP_ID, otherTenant);
        Participant other = applicationParticipant(otherTenant, alice.getId());
        assertRefused(ENTITIES_SERVICE, "findById", other, readRow, "@" + otherTenant);

        // the administrator sees the grant where it was made and what it explains
        List<Grant> grants = await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, personHere)));
        assertEquals(List.of(grant), grants);
        AccessExplanation explained = await(runAsOrganization(() -> access.explain(TEST_APP_ID, subject, AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_READ), personHere)));
        assertTrue(explained.allowed());
        assertEquals(List.of(grant), explained.through());
        assertFalse(await(runAsOrganization(() -> access.explain(TEST_APP_ID, subject, AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_READ), carHere))).allowed());

        // a grant on the definition reaches it in every tenant, including one no grant was made in
        Resource personEverywhere = new Resource(AuthzConstants.ENTITY_DEFINITION_TYPE, person.getId());
        Grant everyTenant = await(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, alice.getId()),
                                                                       AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER), personEverywhere)));
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", other, readRow)), "the viewer granted on the definition was never admitted");
        assertRefused(ENTITIES_SERVICE, "bulkUpdate", other, List.of(person.getId(), List.of()), AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_EDIT));
        // a grant on the definition reaches the admin repository too, whichever tenants a call selects
        authorize(ADMIN_ENTITIES_SERVICE, "count", other, List.of(person.getId(), List.of(tenantId, otherTenant)));
        authorize(ADMIN_ENTITIES_SERVICE, "findById", other, adminRow);
        assertRefused(ADMIN_ENTITIES_SERVICE, "deleteById", other, adminRow, AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_DELETE));
        assertRefused(ENTITIES_SERVICE, "findById", other, List.of(car.getId(), "row-1"), AuthzUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(car.getId(), otherTenant)));
        Resource personThere = new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(person.getId(), otherTenant));
        assertEquals(List.of(everyTenant), await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, personThere))));

        // a grant on the application reaches every definition in every tenant
        Grant everywhere = await(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, alice.getId()),
                                                                      AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER),
                                                                      new Resource(AuthzConstants.APPLICATION_TYPE, TEST_APP_ID))));
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", other, List.of(car.getId(), "row-1"))), "the viewer granted on the application was never admitted");
        assertEquals(List.of(everyTenant, everywhere), await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, personThere))));

        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, personHere, grant.id())));
        assertTrue(awaitUntil(() -> !admitted(ENTITIES_SERVICE, "bulkUpdate", caller, List.of(person.getId(), List.of()))), "the revoked editor was still admitted");
        // the grants made on the definition and the application still reach it in the tenant, and are all that do
        assertEquals(List.of(everyTenant, everywhere), await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, personHere))));
        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, personEverywhere, everyTenant.id())));
        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, new Resource(AuthzConstants.APPLICATION_TYPE, TEST_APP_ID), everywhere.id())));
    }

    @Test
    public void aGrantIsMadeOnWhatTheApplicationHoldsToOneOfItsOwnUsers() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        String editor = AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.EDITOR);
        ApplicationParticipantIdentity bob = endUser(TEST_APP_ID, "tenant-" + suffix());
        Subject subject = new Subject(SubjectType.USER, bob.getId());
        Resource tenant = new Resource(AuthzConstants.TENANT_TYPE, bob.getTenantId());

        // a grant on a project, which the application's store knows nothing of
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant(TEST_APP_ID, subject, editor, new Resource("project", "p"))));
        // a grant on a definition the application does not hold, alone or within a tenant
        String theirs = DomainUtil.createEntityDefinitionId(new ApplicationKey(TEST_ORG_ID, "another-app"), person.getName());
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant(TEST_APP_ID, subject, editor, new Resource(AuthzConstants.ENTITY_DEFINITION_TYPE, theirs))));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> access.grant(TEST_APP_ID, subject, editor, new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(theirs, bob.getTenantId())))));
        // a definition within a tenant named without the tenant
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant(TEST_APP_ID, subject, editor, new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, person.getId()))));
        // a grant to the organization's member, who is not one of the application's users
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, TEST_ORGANIZATION_PARTICIPANT.getId()), editor, tenant)));
        // a grant of a role the store does not define
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant(TEST_APP_ID, subject, "project.editor", tenant)));
        // a grant on a tenant of the application's own role, which is homed above the tenant
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(AuthzConstants.APPLICATION_TYPE, AuthzConstants.ADMIN), tenant)));
        // a grant on a definition within a tenant of a role homed at the tenant
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(AuthzConstants.TENANT_TYPE, AuthzConstants.ADMIN),
                                                    new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(person.getId(), bob.getTenantId())))));
        // a grant in an application of another organization, or of none
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant("no-such-application", subject, editor, tenant)));
    }

    @Test
    public void deletingATenantTakesItsGrantsAndMembershipsOutOfTheStore() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        Tenant tenant = await(runAsOrganization(() -> tenantService.createTenantIfNotExist(TEST_APP_ID, "Hooli " + suffix(), "A customer")));
        ApplicationParticipantIdentity bob = endUser(TEST_APP_ID, tenant.getId());
        Subject subject = new Subject(SubjectType.USER, bob.getId());
        String store = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID);
        String tenantObject = AuthzUtil.object(AuthzConstants.TENANT_TYPE, tenant.getId());
        String withinObject = AuthzUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(person.getId(), tenant.getId()));
        Grant onTenant = await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(AuthzConstants.TENANT_TYPE, AuthzConstants.ADMIN),
                                                                    new Resource(AuthzConstants.TENANT_TYPE, tenant.getId()))));
        Grant onRows = await(runAsOrganization(() -> access.grant(TEST_APP_ID, subject, AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER),
                                                                  new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.idOf(withinObject)))));
        assertTrue(await(relationships.holds(store, new RelationshipTuple(AuthzUtil.object(AuthzConstants.USER_TYPE, bob.getId()), AuthzConstants.MEMBER_RELATION, tenantObject))));

        await(runAsOrganization(() -> tenantService.deleteById(tenant.getId())));

        assertTrue(await(relationships.read(store, tenantObject)).isEmpty());
        assertTrue(await(relationships.read(store, withinObject)).isEmpty());
        assertTrue(await(relationships.read(store, AuthzUtil.bindingObject(tenantObject, onTenant.id()))).isEmpty());
        assertTrue(await(relationships.read(store, AuthzUtil.bindingObject(withinObject, onRows.id()))).isEmpty());
    }

    @Test
    public void deletingAnApplicationDeletesItsStoreWithEverythingInIt() throws Exception {
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Store " + suffix(), "lifecycle", null)));
        String store = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, application.getId());

        // created with its store, running the kernel model, and the record the worker keeps in step
        AuthzStore record = await(stores.findById(store));
        assertNotNull(record);
        assertEquals(TEST_ORG_ID, record.getOrganizationId());
        assertNotNull(await(storeService.modelId(store)));
        ApplicationParticipantIdentity bob = endUser(application.getId(), "tenant-" + suffix());
        String me = AuthzUtil.object(AuthzConstants.USER_TYPE, bob.getId());
        assertTrue(await(relationships.holds(store, new RelationshipTuple(me, AuthzConstants.END_USER_RELATION, AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, store)))));
        assertTrue(await(relationships.holds(store, new RelationshipTuple(me, AuthzConstants.MEMBER_RELATION, AuthzUtil.object(AuthzConstants.TENANT_TYPE, bob.getTenantId())))));
        assertTrue(await(relationships.holds(AuthzStoreService.SYSTEM, new RelationshipTuple(me, AuthzConstants.END_USER_RELATION, AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, store)))));

        await(runAsOrganization(() -> applicationService.deleteById(application.getId())));

        assertNull(await(stores.findById(store)));
        assertThrows(ExecutionException.class, () -> await(storeService.modelId(store)));
        assertThrows(ExecutionException.class, () -> await(relationships.read(store, AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, store))));
    }

    @Test
    public void anApplicationCreatedAgainIsPlacedInTheStoreItsNameWasLeft() throws Exception {
        String name = "Store " + suffix();
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(name, "lifecycle", null)));
        String store = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, application.getId());
        RelationshipTuple placed = new RelationshipTuple(AuthzConstants.EVERYONE, AuthzConstants.PLACED_RELATION, AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, store));
        await(runAsOrganization(() -> applicationService.deleteById(application.getId())));

        // a reconcile of the store's record that runs while the application is deleted creates the store again,
        // running the application's model and holding nothing
        AuthzModel model = await(directory.findSystemDefinitions().map(system -> generator.applicationModel(system, List.of())));
        await(storeService.ensureStore(store).compose(id -> relationships.ensureModelWithRoles(store, model)));
        assertFalse(await(relationships.holds(store, placed)));

        Application again = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(name, "lifecycle", null)));
        try {
            assertEquals(application.getId(), again.getId());
            assertTrue(await(relationships.holds(store, placed)));
        } finally {
            await(runAsOrganization(() -> applicationService.deleteById(again.getId())));
        }
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

    private ApplicationParticipantIdentity endUser(String applicationId, String tenantId) throws Exception {
        ApplicationParticipantIdentity user = new ApplicationParticipantIdentity();
        user.setEmail("end-user-" + suffix() + "@kinotic.test");
        user.setDisplayName("End User");
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(applicationId);
        user.setTenantId(tenantId);
        return await(identityService.createUser(user, "End-User-1"));
    }

    private Throwable failure(java.util.function.Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAsOrganization(call::get))).getCause();
    }
}
