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
import org.kinotic.app.api.services.ApplicationServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.AuthzResourceC3Decorator;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.TenantService;
import org.kinotic.management.api.services.security.ApplicationAccessService;
import org.kinotic.management.api.services.security.PermissionService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * users, or to every user of one of its tenants with one grant, which a user created in the tenant later holds
 * too; a user reaches a resource of a type the application's own services declare through its grant on its
 * tenant; deleting an application deletes its store with everything in it; and an application created again under
 * the same id is placed in its store whatever model the store was left running.
 */
@SpringBootTest
public class ApplicationStoreTests extends KinoticTestBase {

    private static final String ENTITIES_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.persistence.api.services.JsonEntitiesRepository";
    private static final String ADMIN_ENTITIES_SERVICE = DomainUtil.APP_API_ZONE + "~org.kinotic.persistence.api.services.AdminJsonEntitiesRepository";
    // what the refusal of a function acting on every tenant's rows says to a participant of an application
    private static final String ACROSS_TENANTS = "across tenants";

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationAccessService access;

    @Autowired
    private PermissionService permissions;

    @Autowired
    private ApplicationRepository applicationRepository;

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

    @Autowired
    private ApplicationServiceDirectory applicationServiceDirectory;

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
        String tenantId = createTenant(TEST_APP_ID);
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
        // the admin repository acts on the rows of the tenants a call names, so no user of the application reaches it
        List<Object> adminRow = List.of(person.getId(), Map.of("entityId", "row-1", "tenantId", tenantId));
        assertRefused(ADMIN_ENTITIES_SERVICE, "findById", caller, adminRow, ACROSS_TENANTS);
        assertRefused(ADMIN_ENTITIES_SERVICE, "count", caller, List.of(person.getId(), List.of(tenantId)), ACROSS_TENANTS);
        assertRefused(ADMIN_ENTITIES_SERVICE, "deleteByQuery", caller, List.of(person.getId(), "*", List.of(tenantId)), ACROSS_TENANTS);
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
        // a grant on the definition leaves its holder in its own tenant: the admin repository still refuses it
        assertRefused(ADMIN_ENTITIES_SERVICE, "count", other, List.of(person.getId(), List.of(tenantId, otherTenant)), ACROSS_TENANTS);
        assertRefused(ADMIN_ENTITIES_SERVICE, "findById", other, adminRow, ACROSS_TENANTS);
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
    public void aRoleOfTheApplicationsOwnAdmitsItsHoldersToTheEntitiesItNames() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        EntityDefinition car = await(runAsOrganization(() -> testData.createCarEntityDefinitionIfNotExists(null))).getLeft();
        String tenantId = createTenant(TEST_APP_ID);
        ApplicationParticipantIdentity bob = endUser(TEST_APP_ID, tenantId);
        Participant caller = applicationParticipant(tenantId, bob.getId());
        String reading = AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_READ);
        String editing = AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_EDIT);
        String deleting = AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_DELETE);
        assertEquals(Set.of(reading, editing, deleting),
                     await(runAsOrganization(() -> access.findPermissions(TEST_APP_ID))).get(AuthzConstants.ENTITY_DEFINITION_TYPE));

        // an accountant reads and edits people and writes cars it does not read
        RoleDefinition accountant = await(runAsOrganization(() -> access.saveRole(TEST_APP_ID, new RoleDefinition(
                null, "Accountant " + suffix(), "keeps the books", false, null, null, Set.of(),
                Map.of(person.getId(), Set.of(reading, editing), car.getId(), Set.of(editing))))));
        assertEquals(AuthzConstants.TENANT_TYPE, accountant.resourceType());
        assertEquals(Set.of(AuthzConstants.APPLICATION_TYPE, AuthzConstants.TENANT_TYPE), accountant.grantableOn());
        assertEquals(accountant, role(accountant.id()));
        Resource tenant = new Resource(AuthzConstants.TENANT_TYPE, tenantId);
        Grant grant = await(runAsOrganization(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, bob.getId()), accountant.id(), tenant)));

        List<Object> personRow = List.of(person.getId(), "row-1");
        List<Object> carRow = List.of(car.getId(), "row-1");
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", caller, personRow)), "the accountant was never admitted to the people");
        authorize(ENTITIES_SERVICE, "bulkUpdate", caller, List.of(person.getId(), List.of(Map.of("id", "row-1"))));
        assertRefused(ENTITIES_SERVICE, "deleteById", caller, personRow, deleting);
        authorize(ENTITIES_SERVICE, "bulkUpdate", caller, List.of(car.getId(), List.of(Map.of("id", "row-1"))));
        assertRefused(ENTITIES_SERVICE, "findById", caller, carRow, reading);
        // the role is held in the tenant it was granted on
        String otherTenant = "tenant-" + suffix();
        ApplicationParticipantIdentity alice = endUser(TEST_APP_ID, otherTenant);
        assertRefused(ENTITIES_SERVICE, "findById", applicationParticipant(otherTenant, alice.getId()), personRow, reading);
        Resource personHere = new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(person.getId(), tenantId));
        AccessExplanation explained = await(runAsOrganization(() -> access.explain(TEST_APP_ID, new Subject(SubjectType.USER, bob.getId()), reading, personHere)));
        assertTrue(explained.allowed());
        assertEquals(List.of(grant), explained.through());

        // an edit takes effect on the grant made already
        RoleDefinition edited = await(runAsOrganization(() -> access.saveRole(TEST_APP_ID, new RoleDefinition(
                accountant.id(), accountant.name(), null, false, null, null, Set.of(deleting), Map.of(car.getId(), Set.of(reading, deleting))))));
        assertEquals(Set.of(deleting), edited.permissions());
        assertEquals(Map.of(car.getId(), Set.of(reading)), edited.definitionPermissions());
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", caller, carRow)), "the edited role never admitted its holder to the cars");
        assertTrue(awaitUntil(() -> !admitted(ENTITIES_SERVICE, "findById", caller, personRow)), "the edited role still admitted its holder to the people");
        authorize(ENTITIES_SERVICE, "deleteById", caller, personRow);

        // a role names definitions the application holds and permissions of their rows, and is the application's
        assertTrue(failure(() -> access.saveRole(TEST_APP_ID, new RoleDefinition(
                null, "Stray", null, false, null, null, Set.of(), Map.of("no.such.definition", Set.of(reading))))).getMessage()
                           .contains("No entity definition of the application"));
        assertTrue(failure(() -> access.saveRole(TEST_APP_ID, new RoleDefinition(
                null, "Reaching", null, false, null, null, Set.of("application_can_manage_access"), Map.of()))).getMessage()
                           .contains("can bundle"));
        assertTrue(failure(() -> access.deleteRole(TEST_APP_ID, accountant.id())).getMessage().contains("still held by grants"));
        assertFalse(await(runAsOrganization(() -> permissions.findRoles())).stream().anyMatch(found -> found.id().equals(accountant.id())));

        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, tenant, grant.id())));
        await(runAsOrganization(() -> access.deleteRole(TEST_APP_ID, accountant.id())));
        assertFalse(roleIds().contains(accountant.id()));
        assertTrue(failure(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.USER, bob.getId()), accountant.id(), tenant))
                           .getMessage().contains("No role of the application"));
    }

    @Test
    public void aGrantIsMadeOnWhatTheApplicationHoldsToOneOfItsOwnUsers() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        String editor = AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.EDITOR);
        ApplicationParticipantIdentity bob = endUser(TEST_APP_ID, createTenant(TEST_APP_ID));
        Subject subject = new Subject(SubjectType.USER, bob.getId());
        Resource tenant = new Resource(AuthzConstants.TENANT_TYPE, bob.getTenantId());

        // a grant on a project, which the application's store knows nothing of
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant(TEST_APP_ID, subject, editor, new Resource("project", "p"))));
        // a grant on a definition the application does not hold, alone or within a tenant
        String theirs = DomainUtil.createEntityDefinitionId(new ApplicationKey(TEST_ORG_ID, "another-app"), person.getName());
        assertInstanceOf(IllegalArgumentException.class, failure(() -> access.grant(TEST_APP_ID, subject, editor, new Resource(AuthzConstants.ENTITY_DEFINITION_TYPE, theirs))));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> access.grant(TEST_APP_ID, subject, editor, new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(theirs, bob.getTenantId())))));
        // a grant on a tenant the application has none of, alone or with a definition
        String noTenant = "tenant-" + suffix();
        String tenantAdmin = AuthzUtil.roleId(AuthzConstants.TENANT_TYPE, AuthzConstants.ADMIN);
        assertEquals("No tenant of the application has id " + noTenant,
                     failure(() -> access.grant(TEST_APP_ID, subject, tenantAdmin, new Resource(AuthzConstants.TENANT_TYPE, noTenant))).getMessage());
        assertEquals("No tenant of the application has id " + noTenant,
                     failure(() -> access.grant(TEST_APP_ID, subject, editor, new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(person.getId(), noTenant)))).getMessage());
        assertTrue(await(relationships.read(AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, TEST_APP_ID), AuthzUtil.object(AuthzConstants.TENANT_TYPE, noTenant))).isEmpty());
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
    public void aGrantIsMadeOnTheTenantTheSystemGaveAUser() throws Exception {
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("System tenants " + suffix(), "system tenants", TenantManagement.SYSTEM)));
        // the system gives the user a tenant of its own, which has no record
        ApplicationParticipantIdentity bob = endUser(application.getId(), null);
        Resource tenant = new Resource(AuthzConstants.TENANT_TYPE, bob.getTenantId());

        Grant grant = await(runAsOrganization(() -> access.grant(application.getId(), new Subject(SubjectType.USER, bob.getId()),
                                                                 AuthzUtil.roleId(AuthzConstants.TENANT_TYPE, AuthzConstants.ADMIN), tenant)));

        assertEquals(List.of(grant), await(runAsOrganization(() -> access.findGrants(application.getId(), tenant))));
        await(runAsOrganization(() -> applicationService.deleteById(application.getId())));
    }

    @Test
    public void aUserReachesAResourceOfTheApplicationsOwnTypeThroughItsGrantOnTheTenant() throws Exception {
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Own types " + suffix(), "own types", TenantManagement.SYSTEM)));
        String appId = application.getId();
        String zone = DomainUtil.applicationZone(TEST_ORG_ID, appId);
        // the application's runtime registers a service of reports, held in a tenant, and one of the charts in a report
        Participant runtime = new DefaultOrganizationParticipant(UUID.randomUUID().toString(), TEST_ORG_ID,
                                                                 Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                        ParticipantConstants.PARTICIPANT_TYPE_MACHINE),
                                                                 List.of());
        await(relationships.bind(AuthzStoreService.SYSTEM, AuthzConstants.APPLICATION_RUNTIME_ROLE,
                                 AuthzUtil.object(AuthzConstants.USER_TYPE, runtime.getId()), AuthzGraphUtil.authzApplication(TEST_ORG_ID, appId)));
        ServiceDefinition reports = ownTypeService("ReportService", "report", AuthzConstants.TENANT_TYPE)
                .addFunction(new FunctionDefinition().setName("findReports"));
        ServiceDefinition charts = ownTypeService("ChartService", "chart", "report")
                .addFunction(new FunctionDefinition().setName("createChart").addParameter("reportId", new StringC3Type()));
        for (ServiceDefinition definition : List.of(reports, charts)) {
            await(runAs(runtime, () -> applicationServiceDirectory.register(new ServiceDirectoryEntry().setApplicationId(appId)
                                                                                                       .setZone(zone)
                                                                                                       .setVersion("1.0.0")
                                                                                                       .setServiceDefinition(definition))));
        }
        String reportService = zone + "~com.acme.reports.ReportService";
        String chartService = zone + "~com.acme.reports.ChartService";
        ApplicationParticipantIdentity bob = endUser(appId, null);
        ApplicationParticipantIdentity carol = endUser(appId, null);
        Participant viewer = applicationUser(appId, bob);
        Participant stranger = applicationUser(appId, carol);
        Resource bobsTenant = new Resource(AuthzConstants.TENANT_TYPE, bob.getTenantId());
        String readReports = AuthzUtil.permissionName("report", AuthzConstants.CAN_READ) + " on " + AuthzUtil.object(AuthzConstants.TENANT_TYPE, bob.getTenantId());

        assertRefused(reportService, "findById", viewer, List.of("r-1"), readReports);

        // the store defines the types' roles once it reconciles to the model the registrations imply
        assertTrue(awaitUntil(() -> {
            boolean granted;
            try {
                await(runAsOrganization(() -> access.grant(appId, new Subject(SubjectType.USER, bob.getId()), "report.viewer", bobsTenant)));
                granted = true;
            } catch (ExecutionException undefined) {
                granted = false;
            }
            return granted;
        }), "report.viewer was never grantable in the application's store");
        await(runAsOrganization(() -> access.grant(appId, new Subject(SubjectType.USER, bob.getId()), "chart.viewer", bobsTenant)));

        // a report and a chart named by id are read by the viewer of the tenant's reports and charts
        assertTrue(awaitUntil(() -> admitted(reportService, "findById", viewer, List.of("r-1"))), "the reports' viewer was never admitted to a report");
        authorize(reportService, "findReports", viewer, List.of());
        authorize(chartService, "findById", viewer, List.of("c-1"));
        // a viewer creates no chart in a report, and a user of another tenant holds nothing in its own
        assertRefused(chartService, "createChart", viewer, List.of("r-1"),
                      AuthzUtil.permissionName("chart", AuthzConstants.CAN_EDIT) + " on " + AuthzUtil.object(AuthzConstants.TENANT_TYPE, bob.getTenantId()));
        assertRefused(reportService, "findById", stranger, List.of("r-1"),
                      AuthzUtil.permissionName("report", AuthzConstants.CAN_READ) + " on " + AuthzUtil.object(AuthzConstants.TENANT_TYPE, carol.getTenantId()));
        assertRefused(chartService, "findById", stranger, List.of("c-1"), AuthzUtil.object(AuthzConstants.TENANT_TYPE, carol.getTenantId()));

        await(runAsOrganization(() -> applicationService.deleteById(appId)));
    }

    @Test
    public void aRoleGrantedToATenantIsHeldByEveryUserOfItNowAndLater() throws Exception {
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        Tenant tenant = await(runAsOrganization(() -> tenantService.createTenantIfNotExist(TEST_APP_ID, "Initech " + suffix(), "A customer")));
        ApplicationParticipantIdentity bob = endUser(TEST_APP_ID, tenant.getId());
        Participant early = applicationParticipant(tenant.getId(), bob.getId());
        Subject everyone = new Subject(SubjectType.TENANT, tenant.getId());
        Resource onTenant = new Resource(AuthzConstants.TENANT_TYPE, tenant.getId());
        Resource application = new Resource(AuthzConstants.APPLICATION_TYPE, TEST_APP_ID);
        Resource personHere = new Resource(AuthzConstants.TENANT_DEFINITION_TYPE, AuthzUtil.tenantDefinitionId(person.getId(), tenant.getId()));
        String viewerRole = AuthzUtil.roleId(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.VIEWER);
        String canRead = AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_READ);
        List<Object> readRow = List.of(person.getId(), "row-1");
        assertRefused(ENTITIES_SERVICE, "findById", early, readRow, canRead);

        Grant grant = await(runAsOrganization(() -> access.grant(TEST_APP_ID, everyone, viewerRole, onTenant)));

        assertEquals(everyone, grant.subject());
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", early, readRow)), "the tenant's user was never admitted");
        // a user created in the tenant after the grant holds it too, a viewer edits nothing, and another tenant's
        // user holds nothing
        ApplicationParticipantIdentity carol = endUser(TEST_APP_ID, tenant.getId());
        assertTrue(awaitUntil(() -> admitted(ENTITIES_SERVICE, "findById", applicationParticipant(tenant.getId(), carol.getId()), readRow)),
                   "a user created after the grant was never admitted");
        assertRefused(ENTITIES_SERVICE, "bulkUpdate", early, List.of(person.getId(), List.of()),
                      AuthzUtil.permissionName(AuthzConstants.ENTITY_DEFINITION_TYPE, AuthzConstants.CAN_EDIT));
        String otherTenant = createTenant(TEST_APP_ID);
        ApplicationParticipantIdentity alice = endUser(TEST_APP_ID, otherTenant);
        assertRefused(ENTITIES_SERVICE, "findById", applicationParticipant(otherTenant, alice.getId()), readRow, "@" + otherTenant);

        // the grant is listed once, and explains what each user of the tenant holds
        assertEquals(List.of(grant), await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, onTenant))));
        AccessExplanation explained = await(runAsOrganization(() -> access.explain(TEST_APP_ID, new Subject(SubjectType.USER, bob.getId()), canRead, personHere)));
        assertTrue(explained.allowed());
        assertEquals(List.of(grant), explained.through());
        assertFalse(await(runAsOrganization(() -> access.explain(TEST_APP_ID, new Subject(SubjectType.USER, alice.getId()), canRead, personHere))).through()
                                                                                                                                              .contains(grant));
        // granting it again is the grant already made
        assertEquals(grant, await(runAsOrganization(() -> access.grant(TEST_APP_ID, everyone, viewerRole, onTenant))));
        // a tenant the application has none of is no subject
        String noTenant = "tenant-" + suffix();
        assertEquals("No tenant of the application has id " + noTenant,
                     failure(() -> access.grant(TEST_APP_ID, new Subject(SubjectType.TENANT, noTenant), viewerRole, application)).getMessage());

        await(runAsOrganization(() -> access.revoke(TEST_APP_ID, onTenant, grant.id())));
        assertTrue(awaitUntil(() -> !admitted(ENTITIES_SERVICE, "findById", early, readRow)), "the tenant's user was still admitted after the revoke");

        // deleting the tenant takes back what its users were granted as a whole, wherever it was granted
        Grant everywhere = await(runAsOrganization(() -> access.grant(TEST_APP_ID, everyone, viewerRole, application)));
        assertTrue(await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, application))).contains(everywhere));
        await(runAsOrganization(() -> tenantService.deleteById(TEST_APP_ID, tenant.getId())));
        assertFalse(await(runAsOrganization(() -> access.findGrants(TEST_APP_ID, application))).contains(everywhere));
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

        await(runAsOrganization(() -> tenantService.deleteById(TEST_APP_ID, tenant.getId())));

        assertTrue(await(relationships.read(store, tenantObject)).isEmpty());
        assertTrue(await(relationships.read(store, withinObject)).isEmpty());
        assertTrue(await(relationships.read(store, AuthzUtil.bindingObject(tenantObject, onTenant.roleId()))).isEmpty());
        assertTrue(await(relationships.read(store, AuthzUtil.bindingObject(withinObject, onRows.roleId()))).isEmpty());
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
    public void anApplicationFoundWithoutItsStoreIsProvisioned() throws Exception {
        String name = "Store " + suffix();
        // the record alone, as a caller finds it while the one that created it is still provisioning its store
        Application stored = new Application(name, "lifecycle");
        stored.setId(DomainUtil.slugifyId(name));
        stored.setOrganizationId(TEST_ORG_ID);
        stored.setTenantManagement(TenantManagement.TENANT_ADMINISTRATORS);
        await(applicationRepository.createSync(stored, TEST_ORG_ID));
        String store = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, stored.getId());
        assertNull(await(stores.findById(store)));

        Application found = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(name, "lifecycle", null)));
        try {
            assertEquals(stored.getId(), found.getId());
            assertNotNull(await(stores.findById(store)));
            assertNotNull(await(storeService.modelId(store)));
        } finally {
            await(runAsOrganization(() -> applicationService.deleteById(found.getId())));
        }
    }

    @Test
    public void anApplicationCreatedAgainIsPlacedInTheStoreItsNameWasLeft() throws Exception {
        String name = "Store " + suffix();
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(name, "lifecycle", null)));
        String store = AuthzGraphUtil.authzApplicationId(TEST_ORG_ID, application.getId());
        RelationshipTuple placed = new RelationshipTuple(AuthzConstants.EVERYONE, AuthzConstants.PLACED_RELATION, AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, store));
        await(runAsOrganization(() -> applicationService.deleteById(application.getId())));

        // a create of the id whose provisioning failed and whose rollback could not delete the store left it,
        // running a model and holding nothing
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

    // a service of an application's own resource type, with a function reading one resource by its id
    private static ServiceDefinition ownTypeService(String name, String type, String parent) {
        ServiceDefinition ret = new ServiceDefinition().setNamespace("com.acme.reports").setName(name);
        ret.setDecorators(List.of(new AuthzResourceC3Decorator().setResourceType(type).setParent(parent)));
        return ret.addFunction(new FunctionDefinition().setName("findById").addParameter("id", new StringC3Type()));
    }

    private static Participant applicationUser(String applicationId, ApplicationParticipantIdentity identity) {
        return new DefaultApplicationParticipant(identity.getId(), TEST_ORG_ID, applicationId, identity.getTenantId(),
                                                 Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                 List.of("USER"));
    }

    private Throwable failure(java.util.function.Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAsOrganization(call::get))).getCause();
    }
}
