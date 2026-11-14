package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.model.persistence.idl.decorators.TenantIdDecorator;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.PendingSignUp;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.SignUpService;
import org.kinotic.domain.internal.api.repositories.PendingSignUpRepository;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.EntityDefinitionService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.internal.api.services.ApplicationStoreProvisioner;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the system store's graph follows the lifecycle writes: an organization, an application, a project,
 * an entity definition and a user each take their place in it as part of the write that creates them and leave
 * it with the write that deletes them; the built-in roles bundle what the system model has; and the user who
 * signs an organization up administers it, which a check through the model answers. An application provisioned
 * while it holds users is given them, as one a migration seeded is by the seed job. An organization created under
 * the id of a deleted one starts with none of its users, and a project deleted and created at once is stored only
 * with its place in the graph.
 */
@SpringBootTest
public class GraphSyncTests extends KinoticTestBase {

    private static final String TENANT_ID_FIELD = "tenantId";

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EntityDefinitionService entityDefinitionService;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private SignUpService signUpService;

    @Autowired
    private PendingSignUpRepository pendingSignUps;

    @Autowired
    private RelationshipService relationships;

    @Autowired
    private AuthzStoreService storeService;

    @Autowired
    private AuthzModelGenerator modelGenerator;

    @Autowired
    private ServiceDirectory serviceDirectory;

    @Autowired
    private AuthzStoreRepository stores;

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private ApplicationStoreProvisioner provisioner;

    @Test
    public void lifecycleWritesPlaceEachRecordInTheGraphAndDeletesRemoveIt() throws Exception {
        String suffix = suffix();
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Graph Sync " + suffix, "graph sync", null)));
        RelationshipTuple applicationInOrganization = new RelationshipTuple(AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, TEST_ORG_ID),
                                                                            AuthzConstants.ORGANIZATION_TYPE,
                                                                            AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId())));
        assertTrue(holds(applicationInOrganization), "the application is in its organization");
        // ensuring an application that exists writes nothing twice and fails on nothing
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Graph Sync " + suffix, "graph sync", null)));

        Project project = new Project();
        project.setOrganizationId(TEST_ORG_ID);
        project.setApplicationId(application.getId());
        project.setName("graph-sync-project");
        Project created = await(runAsOrganization(() -> projectService.createProjectIfNotExist(project)));
        RelationshipTuple projectInApplication = new RelationshipTuple(AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId())),
                                                                       AuthzConstants.APPLICATION_TYPE,
                                                                       AuthzUtil.object(ProjectService.RESOURCE_TYPE, AuthzGraphUtil.authzId(ProjectService.RESOURCE_TYPE, TEST_ORG_ID, created.getId())));
        assertTrue(holds(projectInApplication), "the project is in its application");

        EntityDefinition definition = definition(application.getId(), created.getId(), suffix);
        EntityDefinition saved = await(runAsOrganization(() -> entityDefinitionService.create(definition)));
        RelationshipTuple definitionInApplication = new RelationshipTuple(AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId())),
                                                                          AuthzConstants.APPLICATION_TYPE,
                                                                          AuthzUtil.object(EntityDefinitionService.RESOURCE_TYPE, saved.getId()));
        assertTrue(holds(definitionInApplication), "the entity definition is in its application");

        await(runAsOrganization(() -> entityDefinitionService.deleteById(saved.getId())));
        assertFalse(holds(definitionInApplication), "a deleted entity definition leaves the graph");
        await(runAsOrganization(() -> projectService.deleteById(created.getId())));
        assertFalse(holds(projectInApplication), "a deleted project leaves the graph");
    }

    @Test
    public void anOrganizationCreatedUnderADeletedOnesIdStartsWithNoneOfItsUsers() throws Exception {
        String name = "Left Org " + suffix();
        Organization first = await(organizationService.createSync(new Organization().setName(name).setDescription("graph sync")));
        OrganizationParticipantIdentity founder = member(first.getId(), "left-founder");
        // a sign-up rolled back while the engine fails leaves this: a founder whose organization's record is gone
        await(organizationService.deleteByIdSync(first.getId()));
        assertNotNull(await(identityService.findById(founder.getId())));

        Organization second = await(organizationService.createSync(new Organization().setName(name).setDescription("graph sync")));

        assertEquals(first.getId(), second.getId());
        assertNull(await(identityService.findById(founder.getId())), "the deleted organization's user is a user of the new one");
        assertTrue(holds(new RelationshipTuple(AuthzConstants.SYSTEM_OBJECT, AuthzConstants.SYSTEM_TYPE,
                                               AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, second.getId()))),
                   "the organization is under the system");
        assertInstanceOf(AlreadyExistsException.class,
                         assertThrows(ExecutionException.class,
                                      () -> await(organizationService.createSync(new Organization().setName(name)))).getCause());
    }

    @Test
    public void aProjectDeletedAndCreatedAtOnceIsStoredOnlyWithItsPlaceInTheGraph() throws Exception {
        String suffix = suffix();
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Graph Race " + suffix, "graph sync", null)));
        for (int i = 0; i < 5; i++) {
            String name = "graph-race-" + suffix + "-" + i;
            Project created = await(runAsOrganization(() -> projectService.createSync(project(application, name))));
            RelationshipTuple projectInApplication = new RelationshipTuple(AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId())),
                                                                           AuthzConstants.APPLICATION_TYPE,
                                                                           AuthzUtil.object(ProjectService.RESOURCE_TYPE, AuthzGraphUtil.authzId(ProjectService.RESOURCE_TYPE, TEST_ORG_ID, created.getId())));

            Future<Void> deletion = runAsOrganization(() -> projectService.deleteByIdSync(created.getId()));
            // refused when it finds the project still stored
            Future<Project> creation = runAsOrganization(() -> projectService.createSync(project(application, name)));
            await(deletion);
            await(creation.otherwiseEmpty());

            boolean stored = await(runAsOrganization(() -> projectService.findById(created.getId()))) != null;
            assertEquals(stored, holds(projectInApplication), "a stored project is in its application, and a deleted one is not");
        }
    }

    @Test
    public void aProjectFoundWhileItsIdMovesToAnotherApplicationIsPlacedOnlyWhereItIsStored() throws Exception {
        String suffix = suffix();
        Application first = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Graph Move A " + suffix, "graph sync", null)));
        Application second = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Graph Move B " + suffix, "graph sync", null)));
        for (int i = 0; i < 5; i++) {
            String name = "graph-move-" + suffix + "-" + i;
            Project created = await(runAsOrganization(() -> projectService.createSync(project(first, name))));
            String object = AuthzUtil.object(ProjectService.RESOURCE_TYPE, AuthzGraphUtil.authzId(ProjectService.RESOURCE_TYPE, TEST_ORG_ID, created.getId()));

            // refused when it finds the project stored by the second application
            Future<Project> found = runAsOrganization(() -> projectService.createProjectIfNotExist(project(first, name)));
            Project underTheSameId = project(second, name);
            underTheSameId.setId(created.getId());
            Future<Project> moved = runAsOrganization(() -> projectService.deleteByIdSync(created.getId()))
                    .compose(v -> runAsOrganization(() -> projectService.createSync(underTheSameId)));
            await(found.otherwiseEmpty());
            await(moved.otherwiseEmpty());

            Project stored = await(runAsOrganization(() -> projectService.findById(created.getId())));
            for (Application application : List.of(first, second)) {
                RelationshipTuple inApplication = new RelationshipTuple(AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId())),
                                                                        AuthzConstants.APPLICATION_TYPE,
                                                                        object);
                assertEquals(stored != null && stored.getApplicationId().equals(application.getId()), holds(inApplication),
                             "a project is in the application storing it and in no other");
            }
        }
    }

    private static Project project(Application application, String name) {
        Project ret = new Project();
        ret.setOrganizationId(TEST_ORG_ID);
        ret.setApplicationId(application.getId());
        ret.setName(name);
        return ret;
    }

    @Test
    public void aNewUserJoinsItsScope() throws Exception {
        OrganizationParticipantIdentity member = member(TEST_ORG_ID, "graph-sync");
        assertTrue(holds(new RelationshipTuple(AuthzGraphUtil.authzUser(member.getId()),
                                               AuthzConstants.MEMBER_RELATION,
                                               AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, TEST_ORG_ID))),
                   "an organization user is a member of its organization");

        // an application user takes its application's tenant policy, so the application must exist; the system gives
        // each of its users a tenant of its own
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Graph Sync Users " + suffix(), "graph sync", TenantManagement.SYSTEM)));
        UserParticipantIdentity endUser = await(identityService.createUser(user(TEST_ORG_ID, application.getId()), "Graph-sync-1"));
        assertTrue(holds(new RelationshipTuple(AuthzUtil.object(AuthzConstants.USER_TYPE, endUser.getId()),
                                               AuthzConstants.END_USER_RELATION,
                                               AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId())))),
                   "an application user is an end user of its application");
    }

    @Test
    public void anApplicationWhoseUsersShareATenantIsProvisionedWithThem() throws Exception {
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Graph Sync Tenants " + suffix(), "graph sync", null)));
        String store = AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId());
        String tenant = AuthzUtil.object(AuthzConstants.TENANT_TYPE, "tenant-" + suffix());
        List<String> users = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ApplicationParticipantIdentity user = (ApplicationParticipantIdentity) user(TEST_ORG_ID, application.getId());
            user.setTenantId(AuthzUtil.idOf(tenant));
            users.add(AuthzGraphUtil.authzUser(await(identityService.createUser(user, "Graph-sync-1")).getId()));
        }
        // taken out of the store, the users are held by their records alone, as those a migration seeds are
        for (String user : users) {
            await(relationships.remove(store, List.of(new RelationshipTuple(user, AuthzConstants.MEMBER_RELATION, tenant))));
        }

        // both users place the one tenant under the application
        await(provisioner.provision(application));

        for (String user : users) {
            assertTrue(await(relationships.holds(store, new RelationshipTuple(user, AuthzConstants.MEMBER_RELATION, tenant))),
                       "a user the application holds is a member of its tenant");
        }
    }

    @Test
    public void builtInRolesBundleWhatTheSystemModelHas() throws Exception {
        AuthzModel model = modelFromDirectory();
        assertTrue(awaitUntil(() -> reconciledTo(model.hash())), "the system store never reconciled to the directory's model");

        assertTrue(model.roles().get("project.editor").contains("project_can_edit"));
        assertTrue(model.roles().get(AuthzConstants.ORGANIZATION_ADMIN_ROLE).containsAll(
                Set.of("project_can_delete", "entity_definition_can_delete", "application_can_edit")));
        for (Map.Entry<String, Set<String>> role : model.roles().entrySet()) {
            Set<String> bundled = await(relationships.read(AuthzStoreService.SYSTEM, AuthzUtil.roleObject(role.getKey())))
                    .stream()
                    .filter(tuple -> AuthzConstants.EVERYONE.equals(tuple.user()))
                    .map(RelationshipTuple::relation)
                    .collect(Collectors.toSet());
            assertEquals(role.getValue(), bundled, "role " + role.getKey() + " bundles what the model has");
        }
    }

    @Test
    public void signingUpMakesTheCreatorTheOrganizationsAdmin() throws Exception {
        String suffix = suffix();
        AuthzModel model = modelFromDirectory();
        assertTrue(awaitUntil(() -> reconciledTo(model.hash())), "the system store never reconciled to the directory's model");
        await(storeService.ensureModel(AuthzStoreService.SYSTEM, model));

        String token = UUID.randomUUID().toString();
        PendingSignUp pending = new PendingSignUp();
        pending.setId(UUID.randomUUID().toString());
        pending.setVerificationToken(token);
        pending.setCreated(new Date());
        pending.setExpiresAt(new Date(System.currentTimeMillis() + 3_600_000));
        pending.setEmail("graph-admin-" + suffix + "@kinotic.test");
        pending.setDisplayName("Graph Admin");
        pending.setAuthType(AuthType.PASSWORD);
        await(pendingSignUps.saveSync(pending));
        OrganizationParticipant admin = (OrganizationParticipant) await(signUpService.completeLocalSignUp(token, "Graph Org " + suffix, "graph sync", "Graph-sync-1"));
        String organizationId = admin.getOrganizationId();
        String user = AuthzGraphUtil.authzUser(admin.getId());
        String organization = AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, organizationId);

        assertTrue(holds(new RelationshipTuple(AuthzConstants.SYSTEM_OBJECT, AuthzConstants.SYSTEM_TYPE, organization)),
                   "the organization is under the system");
        assertTrue(holds(new RelationshipTuple(user, AuthzConstants.MEMBER_RELATION, organization)), "the creator is a member");

        // the binding of the organization admin role reaches the organization and everything created inside it
        assertTrue(await(relationships.check(AuthzStoreService.SYSTEM, new RelationshipTuple(user, "application_can_edit", organization), Consistency.HIGHER_CONSISTENCY)));
        DefaultOrganizationParticipant creator = new DefaultOrganizationParticipant(admin.getId(), organizationId,
                                                                                    Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                           ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                                                    List.of("ADMIN"));
        Application application = await(runAs(creator, () -> applicationService.createApplicationIfNotExist("Graph Admin App", "graph sync", null)));
        String applicationObject = AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId()));
        assertTrue(await(relationships.check(AuthzStoreService.SYSTEM, new RelationshipTuple(user, "application_can_edit", applicationObject), Consistency.HIGHER_CONSISTENCY)));
        assertTrue(await(relationships.check(AuthzStoreService.SYSTEM, new RelationshipTuple(user, "project_can_delete", applicationObject), Consistency.HIGHER_CONSISTENCY)));
        // and nothing outside the organization
        assertFalse(await(relationships.check(AuthzStoreService.SYSTEM, new RelationshipTuple(user, "application_can_edit",
                                                                    AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, TEST_ORG_ID)), Consistency.HIGHER_CONSISTENCY)));
    }

    private boolean holds(RelationshipTuple relationship) throws Exception {
        return await(relationships.holds(AuthzStoreService.SYSTEM, relationship));
    }

    private AuthzModel modelFromDirectory() throws Exception {
        return modelGenerator.systemModel(await(serviceDirectory.findSystemDefinitions()));
    }

    private boolean reconciledTo(String hash) throws Exception {
        AuthzStore store = await(stores.findById(AuthzStore.SYSTEM));
        ReconcileState<AuthzModelRevision> state = store.getState();
        return state.isReconciled() && state.getObserved() != null && hash.equals(state.getObserved().hash());
    }

    private EntityDefinition definition(String applicationId, String projectId, String suffix) {
        ObjectC3Type schema = testDataService.createPersonSchema(MultiTenancyType.SHARED)
                                             .addProperty(TENANT_ID_FIELD, new StringC3Type(), List.of(new TenantIdDecorator()));
        EntityDefinition definition = new EntityDefinition();
        definition.setName("GraphPerson" + suffix);
        definition.setOrganizationId(TEST_ORG_ID);
        definition.setApplicationId(applicationId);
        definition.setProjectId(projectId);
        definition.setDescription("A person the graph sync test defines and deletes");
        definition.setSchema(schema);
        return definition;
    }

    private static UserParticipantIdentity user(String organizationId, String applicationId) {
        ApplicationParticipantIdentity user = new ApplicationParticipantIdentity();
        user.setEmail("graph-" + suffix() + "@kinotic.test");
        user.setDisplayName("Graph User");
        user.setOrganizationId(organizationId);
        user.setApplicationId(applicationId);
        return user;
    }
}
