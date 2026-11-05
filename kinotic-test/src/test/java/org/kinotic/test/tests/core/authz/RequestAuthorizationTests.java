package org.kinotic.test.tests.core.authz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.model.security.DelegateType;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.EntityDefinitionService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.api.services.security.PermissionService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a request is authorized as the gateway and the MCP endpoint authorize it, against the contracts the
 * directory holds and the system store: Sally, an editor of project A, edits A and is refused on B, with the
 * positional body a STOMP request carries and the named one a tool call carries; a CLI delegate of hers acts as
 * her within the CLI's ceiling; a member with no grant is refused the organization's members and its viewer is not; a check naming an
 * application the member's scope lacks is made on the organization; a function with no check passes on its
 * zone; and the organization a migration seeds is administered by its creator.
 */
@SpringBootTest
public class RequestAuthorizationTests extends KinoticTestBase {

    private static final String PROJECT_SERVICE = "management-api~org.kinotic.management.api.services.ProjectService";
    private static final String ENTITY_DEFINITION_SERVICE = "management-api~org.kinotic.management.api.services.EntityDefinitionService";
    private static final String MEMBER_SERVICE = "management-api~org.kinotic.management.api.services.security.MemberService";
    private static final String PERMISSION_SERVICE = "management-api~org.kinotic.management.api.services.security.PermissionService";
    // the organization V2__kinotic_test_users seeds, and the user it names as the creator
    private static final String SEEDED_ORGANIZATION_ID = TEST_ORG_ID;
    private static final String SEEDED_CREATOR_ID = "00000000-0000-0000-0000-000000000002";

    @Autowired
    private PermissionService permissions;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EntityDefinitionService entityDefinitionService;

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private RelationshipService relationships;

    @Autowired
    private AuthzStoreRepository stores;

    @Autowired
    private AuthzModelGenerator modelGenerator;

    @Autowired
    private ServiceDirectory serviceDirectory;

    @BeforeEach
    public void awaitSystemModel() throws Exception {
        AuthzModel model = modelGenerator.systemModel(await(serviceDirectory.findSystemDefinitions()));
        assertTrue(awaitUntil(() -> reconciledTo(model.hash())), "the system store never reconciled to the directory's model");
    }

    @Test
    public void anEditorOfOneProjectEditsItAndIsRefusedOnAnother() throws Exception {
        Application application = application();
        Project a = project(application);
        Project b = project(application);
        OrganizationParticipantIdentity sally = member(TEST_ORG_ID, "request");
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "project.editor",
                                                        new Resource(ProjectService.RESOURCE_TYPE, a.getId()))));
        Participant caller = participant(sally.getId(), Map.of());

        // a STOMP request carries the arguments in order, a tool call by name; both name the project the same way
        authorize(PROJECT_SERVICE, "save", caller, List.of(a));
        authorizeToolCall(PROJECT_SERVICE, "save", caller, Map.of("entity", a));
        assertRefused(PROJECT_SERVICE, "save", caller, List.of(b), "project:" + AuthzGraphUtil.authzId(ProjectService.RESOURCE_TYPE, TEST_ORG_ID, b.getId()));
        assertToolCallRefused(PROJECT_SERVICE, "save", caller, Map.of("entity", b), "project:" + AuthzGraphUtil.authzId(ProjectService.RESOURCE_TYPE, TEST_ORG_ID, b.getId()));

        // editing implies reading, and a declared permission is checked on the project the argument names
        authorize(PROJECT_SERVICE, "findDependencies", caller, List.of(a.getId()));
        authorize(PROJECT_SERVICE, "retryRepoInitialization", caller, List.of(a.getId()));
        assertRefused(PROJECT_SERVICE, "findDependencies", caller, List.of(b.getId()), "project_can_read");
        // a read by id is admitted unchecked: the service answers it by what the caller may see
        authorize(PROJECT_SERVICE, "findById", caller, List.of(b.getId()));
        // an editor does not delete
        assertRefused(PROJECT_SERVICE, "deleteById", caller, List.of(a.getId()), "project_can_delete");
        // a request naming no project is refused before the engine is asked
        Project nameless = new Project();
        nameless.setName("nameless");
        assertRefused(PROJECT_SERVICE, "save", caller, List.of(nameless), "entity.id");
        // a listing across the organization is admitted by the zone alone and filtered to what the caller may see
        authorize(PROJECT_SERVICE, "findAll", caller, List.of(Map.of("pageNumber", 0, "pageSize", 10)));
    }

    @Test
    public void anEditorOfAnEntityDefinitionPublishesItAndIsRefusedUnPublishingIt() throws Exception {
        Application application = application();
        EntityDefinition definition = definition(application, project(application));
        OrganizationParticipantIdentity sally = member(TEST_ORG_ID, "request");
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "entity_definition.editor",
                                                        new Resource(EntityDefinitionService.RESOURCE_TYPE, definition.getId()))));
        Participant caller = participant(sally.getId(), Map.of());

        authorize(ENTITY_DEFINITION_SERVICE, "publish", caller, List.of(definition.getId()));
        // un-publishing drops the definition's rows, so it takes what deleting the definition takes
        assertRefused(ENTITY_DEFINITION_SERVICE, "unPublish", caller, List.of(definition.getId()), "entity_definition_can_delete");
        assertToolCallRefused(ENTITY_DEFINITION_SERVICE, "unPublish", caller, Map.of("entityDefinitionId", definition.getId()), "entity_definition_can_delete");
        assertRefused(ENTITY_DEFINITION_SERVICE, "deleteById", caller, List.of(definition.getId()), "entity_definition_can_delete");

        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "entity_definition.admin",
                                                        new Resource(EntityDefinitionService.RESOURCE_TYPE, definition.getId()))));
        assertTrue(awaitUntil(() -> admitted(ENTITY_DEFINITION_SERVICE, "unPublish", caller, List.of(definition.getId()))),
                   "the administrator of the definition never un-published it");
    }

    @Test
    public void aDelegateActsWithItsOwnersAuthorityWithinItsCeiling() throws Exception {
        Project a = project(application());
        OrganizationParticipantIdentity sally = member(TEST_ORG_ID, "request");
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "project.admin",
                                                        new Resource(ProjectService.RESOURCE_TYPE, a.getId()))));
        Participant delegate = participant("cli-" + suffix(), Map.of(DomainUtil.ON_BEHALF_OF_METADATA_KEY, sally.getId(),
                                                                     DomainUtil.DELEGATE_TYPE_METADATA_KEY, DelegateType.CLI.name()));

        authorize(PROJECT_SERVICE, "save", delegate, List.of(a));
        assertRefused(PROJECT_SERVICE, "save", participant("cli-" + suffix(), Map.of()), List.of(a), "project_can_edit");
        // sally may delete A, which is beyond what the CLI exercises of hers
        assertRefused(PROJECT_SERVICE, "deleteById", delegate, List.of(a.getId()), "can_delete");
    }

    @Test
    public void theOrganizationsOwnFunctionsAreCheckedOnTheCallersOrganization() throws Exception {
        OrganizationParticipantIdentity member = member(TEST_ORG_ID, "request");
        Participant caller = participant(member.getId(), Map.of());
        // every application's members: a null application id, then the page
        List<Object> listing = Arrays.asList(null, Map.of("pageNumber", 0, "pageSize", 10));

        assertRefused(MEMBER_SERVICE, "findMembers", caller, listing, "organization:" + TEST_ORG_ID);

        // a listing of the caller's own access is admitted by the zone alone
        authorize(PERMISSION_SERVICE, "listAccessible", caller, List.of(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ));

        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, member.getId()), "organization.viewer",
                                                        new Resource(AuthzConstants.ORGANIZATION_TYPE, TEST_ORG_ID))));
        assertTrue(awaitUntil(() -> admitted(MEMBER_SERVICE, "findMembers", caller, listing)), "the viewer never saw the members");
        assertRefused(MEMBER_SERVICE, "removeMember", caller, List.of(member.getId()), "organization_can_manage_members");

        // re-syncing the projects' index names no application, so the check is made on the organization, which
        // the organization's viewer holds no project permission on; an editor of projects granted on the
        // organization holds every project's
        assertRefused(PROJECT_SERVICE, "syncIndex", caller, List.of(), "project_can_edit on organization:" + TEST_ORG_ID);
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, member.getId()), "project.editor",
                                                        new Resource(AuthzConstants.ORGANIZATION_TYPE, TEST_ORG_ID))));
        assertTrue(awaitUntil(() -> admitted(PROJECT_SERVICE, "syncIndex", caller, List.of())), "the editor of projects never re-synced them");
    }

    @Test
    public void theSeededOrganizationIsAdministeredByItsCreator() throws Exception {
        String organization = AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, SEEDED_ORGANIZATION_ID);
        RelationshipTuple manages = new RelationshipTuple(AuthzGraphUtil.authzUser(SEEDED_CREATOR_ID),
                                                          AuthzUtil.permissionName(AuthzConstants.ORGANIZATION_TYPE, "can_manage_access"),
                                                          organization);
        assertTrue(awaitUntil(() -> await(relationships.check(AuthzStoreService.SYSTEM, manages, Consistency.HIGHER_CONSISTENCY))),
                   "the creator was never bound as the administrator");

        // every server that started swept the organization and bound its creator under the same binding
        Subject creator = new Subject(SubjectType.USER, AuthzUtil.idOf(AuthzGraphUtil.authzUser(SEEDED_CREATOR_ID)));
        long creatorBindings = await(relationships.findGrants(AuthzStoreService.SYSTEM, organization))
                .stream()
                .filter(grant -> AuthzConstants.ORGANIZATION_ADMIN_ROLE.equals(grant.roleId()) && creator.equals(grant.subject()))
                .count();
        assertEquals(1, creatorBindings);
    }

    private static Participant participant(String id, Map<String, String> metadata) {
        Map<String, String> all = new HashMap<>(metadata);
        all.put(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER);
        return new DefaultOrganizationParticipant(id, TEST_ORG_ID, all, List.of());
    }

    private Application application() throws Exception {
        return await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Authorization " + suffix(), "requests", null)));
    }

    private Project project(Application application) throws Exception {
        Project project = new Project();
        project.setName("Authorized Project " + suffix());
        project.setOrganizationId(TEST_ORG_ID);
        project.setApplicationId(application.getId());
        return await(runAsOrganization(() -> projectService.createProjectIfNotExist(project)));
    }

    private EntityDefinition definition(Application application, Project project) throws Exception {
        EntityDefinition definition = new EntityDefinition();
        definition.setName("AuthorizedPerson" + suffix());
        definition.setOrganizationId(TEST_ORG_ID);
        definition.setApplicationId(application.getId());
        definition.setProjectId(project.getId());
        definition.setDescription("A person the request authorization test defines");
        definition.setSchema(testDataService.createPersonSchema(MultiTenancyType.NONE, false));
        return await(runAsOrganization(() -> entityDefinitionService.create(definition)));
    }


    private boolean reconciledTo(String hash) throws Exception {
        AuthzStore store = await(stores.findById(AuthzStore.SYSTEM));
        ReconcileState<AuthzModelRevision> state = store.getState();
        return state.isReconciled() && state.getObserved() != null && hash.equals(state.getObserved().hash());
    }
}
