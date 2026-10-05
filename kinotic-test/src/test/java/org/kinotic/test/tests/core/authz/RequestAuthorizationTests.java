package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.api.services.security.PermissionService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a request is authorized as the gateway and the MCP endpoint authorize it, against the contracts the
 * directory holds and the platform store: Sally, an editor of project A, edits A and is refused on B, with the
 * positional body a STOMP request carries and the named one a tool call carries; a delegate of hers acts as
 * her; a member with no grant is refused the organization's members and its viewer is not; a check naming an
 * application the member's scope lacks is made on the organization; a function with no check passes on its
 * zone; and the organization a migration seeds is administered by its creator.
 */
@SpringBootTest
public class RequestAuthorizationTests extends KinoticTestBase {

    private static final String PROJECT_SERVICE = "management-api~org.kinotic.management.api.services.ProjectService";
    private static final String MEMBER_SERVICE = "management-api~org.kinotic.management.api.services.security.MemberService";
    private static final String PERMISSION_SERVICE = "management-api~org.kinotic.management.api.services.security.PermissionService";
    // the organization V2__kinotic_test_users seeds, and the user it names as the creator
    private static final String SEEDED_ORGANIZATION_ID = "kinotic-test";
    private static final String SEEDED_CREATOR_ID = "00000000-0000-0000-0000-000000000002";

    @Autowired
    private PermissionService permissions;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private RelationshipService relationships;

    @Autowired
    private AuthzStoreService storeService;

    @Autowired
    private AuthzStoreRepository stores;

    @Autowired
    private AuthzModelGenerator modelGenerator;

    @Autowired
    private ServiceDirectory serviceDirectory;

    private String modelId;

    @BeforeEach
    public void awaitPlatformModel() throws Exception {
        AuthzModel model = modelGenerator.platformModel(await(serviceDirectory.findSystemContracts()));
        assertTrue(awaitUntil(() -> reconciledTo(model.hash())), "the platform store never reconciled to the directory's model");
        modelId = await(storeService.modelId(AuthzStoreService.PLATFORM));
    }

    @Test
    public void anEditorOfOneProjectEditsItAndIsRefusedOnAnother() throws Exception {
        Application application = application();
        Project a = project(application);
        Project b = project(application);
        UserParticipantIdentity sally = member();
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectKind.USER, sally.getId()), "project.editor",
                                                        new Resource(ProjectService.RESOURCE_TYPE, a.getId()))));
        Participant caller = participant(sally.getId(), Map.of());

        // a STOMP request carries the arguments in order, a tool call by name; both name the project the same way
        authorize(PROJECT_SERVICE, "save", caller, EventConstants.CONTENT_TYPE_JSON, List.of(a));
        authorize(PROJECT_SERVICE, "save", caller, EventConstants.CONTENT_TYPE_NAMED_JSON, Map.of("entity", a));
        assertRefused(PROJECT_SERVICE, "save", caller, EventConstants.CONTENT_TYPE_JSON, List.of(b), "project:" + b.getId());
        assertRefused(PROJECT_SERVICE, "save", caller, EventConstants.CONTENT_TYPE_NAMED_JSON, Map.of("entity", b), "project:" + b.getId());

        // editing implies viewing, and a declared permission is checked on the project the argument names
        authorize(PROJECT_SERVICE, "findDependencies", caller, EventConstants.CONTENT_TYPE_JSON, List.of(a.getId()));
        authorize(PROJECT_SERVICE, "retryRepoInitialization", caller, EventConstants.CONTENT_TYPE_JSON, List.of(a.getId()));
        assertRefused(PROJECT_SERVICE, "findDependencies", caller, EventConstants.CONTENT_TYPE_JSON, List.of(b.getId()), "project_can_view");
        // a read by id is admitted unchecked: the service answers it by what the caller may see
        authorize(PROJECT_SERVICE, "findById", caller, EventConstants.CONTENT_TYPE_JSON, List.of(b.getId()));
        // an editor does not delete
        assertRefused(PROJECT_SERVICE, "deleteById", caller, EventConstants.CONTENT_TYPE_JSON, List.of(a.getId()), "project_can_delete");
        // a request naming no project is refused before the engine is asked
        Project nameless = new Project();
        nameless.setName("nameless");
        assertRefused(PROJECT_SERVICE, "save", caller, EventConstants.CONTENT_TYPE_JSON, List.of(nameless), "entity.id");
        // a listing across the organization is admitted by the zone alone and filtered to what the caller may see
        authorize(PROJECT_SERVICE, "findAll", caller, EventConstants.CONTENT_TYPE_JSON, List.of(Map.of("pageNumber", 0, "pageSize", 10)));
    }

    @Test
    public void aDelegateActsWithItsOwnersAuthority() throws Exception {
        Project a = project(application());
        UserParticipantIdentity sally = member();
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectKind.USER, sally.getId()), "project.editor",
                                                        new Resource(ProjectService.RESOURCE_TYPE, a.getId()))));
        Participant delegate = participant("cli-" + suffix(), Map.of(DomainUtil.ON_BEHALF_OF_METADATA_KEY, sally.getId()));

        authorize(PROJECT_SERVICE, "save", delegate, EventConstants.CONTENT_TYPE_JSON, List.of(a));
        assertRefused(PROJECT_SERVICE, "save", participant("cli-" + suffix(), Map.of()), EventConstants.CONTENT_TYPE_JSON, List.of(a), "project_can_edit");
    }

    @Test
    public void theOrganizationsOwnFunctionsAreCheckedOnTheCallersOrganization() throws Exception {
        UserParticipantIdentity member = member();
        Participant caller = participant(member.getId(), Map.of());
        // every application's members: a null application id, then the page
        List<Object> listing = Arrays.asList(null, Map.of("pageNumber", 0, "pageSize", 10));

        assertRefused(MEMBER_SERVICE, "findMembers", caller, EventConstants.CONTENT_TYPE_JSON, listing, "organization:" + TEST_ORG_ID);

        // a listing of the caller's own access is admitted by the zone alone
        authorize(PERMISSION_SERVICE, "listAccessible", caller, EventConstants.CONTENT_TYPE_JSON, List.of(ProjectService.RESOURCE_TYPE, AuthzUtil.CAN_VIEW));

        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectKind.USER, member.getId()), "organization.viewer",
                                                        new Resource(AuthzUtil.ORGANIZATION_TYPE, TEST_ORG_ID))));
        assertTrue(awaitUntil(() -> admitted(MEMBER_SERVICE, "findMembers", caller, listing)), "the viewer never saw the members");
        assertRefused(MEMBER_SERVICE, "removeMember", caller, EventConstants.CONTENT_TYPE_JSON, List.of(member.getId()), "organization_can_manage_members");

        // re-syncing the projects' index names no application, so the check is made on the organization, which
        // the organization's viewer holds no project permission on; an editor of projects granted on the
        // organization holds every project's
        assertRefused(PROJECT_SERVICE, "syncIndex", caller, EventConstants.CONTENT_TYPE_JSON, List.of(), "project_can_edit on organization:" + TEST_ORG_ID);
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectKind.USER, member.getId()), "project.editor",
                                                        new Resource(AuthzUtil.ORGANIZATION_TYPE, TEST_ORG_ID))));
        assertTrue(awaitUntil(() -> admitted(PROJECT_SERVICE, "syncIndex", caller, List.of())), "the editor of projects never re-synced them");
    }

    @Test
    public void theSeededOrganizationIsAdministeredByItsCreator() throws Exception {
        String organization = AuthzUtil.object(AuthzUtil.ORGANIZATION_TYPE, SEEDED_ORGANIZATION_ID);
        RelationshipTuple manages = new RelationshipTuple(AuthzUtil.object(AuthzUtil.USER_TYPE, SEEDED_CREATOR_ID),
                                                          AuthzUtil.permissionName(AuthzUtil.ORGANIZATION_TYPE, "can_manage_access"),
                                                          organization);
        assertTrue(awaitUntil(() -> await(relationships.check(AuthzStoreService.PLATFORM, modelId, manages, Consistency.HIGHER_CONSISTENCY))),
                   "the creator was never bound as the administrator");

        // every server that started swept the organization, and found it administered after the first
        String adminRole = AuthzUtil.object(AuthzUtil.ROLE_TYPE, AuthzUtil.ORGANIZATION_ADMIN_ROLE);
        int adminBindings = 0;
        for (RelationshipTuple tuple : await(relationships.read(AuthzStoreService.PLATFORM, organization))) {
            if (AuthzUtil.ROLE_BINDING_RELATION.equals(tuple.relation())
                    && await(relationships.holds(AuthzStoreService.PLATFORM, new RelationshipTuple(adminRole, AuthzUtil.ROLE_RELATION, tuple.user())))) {
                adminBindings++;
            }
        }
        assertEquals(1, adminBindings);
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

    private UserParticipantIdentity member() throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("request-" + suffix() + "@kinotic.test");
        user.setDisplayName("Request User");
        user.setOrganizationId(TEST_ORG_ID);
        return await(identityService.createUser(user, "Request-1"));
    }

    private boolean reconciledTo(String hash) throws Exception {
        AuthzStore store = await(stores.findById(AuthzStore.PLATFORM));
        ReconcileState<AuthzModelRevision> state = store.getState();
        return state.isReconciled() && state.getObserved() != null && hash.equals(state.getObserved().hash());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
