package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.security.Group;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.api.services.security.PermissionService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the console's access control over the platform store: custom roles defined from the model's catalog
 * and deleted once unused, grants reaching down the containment tree and stopping when revoked, groups granting
 * through their members, and the caller's own access listed and explained.
 */
@SpringBootTest
public class PermissionServiceTests extends KinoticTestBase {

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

    // The master ticks every two seconds, then writes the model and brings the built-in roles in step, which
    // every grant below binds on
    @BeforeEach
    public void awaitPlatformModel() throws Exception {
        AuthzModel model = modelGenerator.platformModel(await(serviceDirectory.findSystemContracts()));
        assertTrue(awaitUntil(() -> reconciledTo(model.hash())), "the platform store never reconciled to the directory's model");
        modelId = await(storeService.platformModelId());
    }

    @Test
    public void customRolesAreDefinedFromTheCatalogAndDeletedOnceUnused() throws Exception {
        Map<String, Set<String>> catalog = await(runAsOrganization(permissions::findPermissions));
        assertTrue(catalog.get(ProjectService.RESOURCE_TYPE).contains("project_can_edit"));

        RoleDefinition saved = await(runAsOrganization(() -> permissions.saveRole(
                new RoleDefinition(null, "Release Manager " + suffix(), "ships projects", false, Set.of("project_can_edit", "project_can_view")))));
        assertNotNull(saved.id());
        assertFalse(saved.builtIn());
        List<RoleDefinition> roles = await(runAsOrganization(permissions::findRoles));
        RoleDefinition editor = roles.stream().filter(role -> role.id().equals("project.editor")).findFirst().orElseThrow();
        assertTrue(editor.builtIn());
        assertEquals("Project Editor", editor.name());
        assertTrue(editor.permissions().contains("project_can_edit"));
        RoleDefinition listed = roles.stream().filter(role -> role.id().equals(saved.id())).findFirst().orElseThrow();
        assertEquals(Set.of("project_can_edit", "project_can_view"), listed.permissions());

        // a permission the model does not have, and a built-in role, are refused
        assertThrows(ExecutionException.class, () -> await(runAsOrganization(() -> permissions.saveRole(
                new RoleDefinition(null, "Pilot", null, false, Set.of("project_can_fly"))))));
        assertThrows(ExecutionException.class, () -> await(runAsOrganization(() -> permissions.saveRole(
                new RoleDefinition("project.editor", "Project Editor", null, true, Set.of("project_can_edit"))))));

        // reshaping the role changes what it bundles
        await(runAsOrganization(() -> permissions.saveRole(new RoleDefinition(saved.id(), saved.name(), null, false, Set.of("project_can_view")))));
        assertEquals(Set.of("project_can_view"), bundled(saved.id()));

        // a role a grant holds stays until the grant is revoked
        Application application = application();
        Resource onApplication = new Resource(AuthzUtil.APPLICATION_TYPE, application.getId());
        Subject holder = user(member());
        Grant grant = await(runAsOrganization(() -> permissions.grant(holder, saved.id(), onApplication)));
        assertThrows(ExecutionException.class, () -> await(runAsOrganization(() -> permissions.deleteRole(saved.id()))));
        await(runAsOrganization(() -> permissions.revoke(onApplication, grant.id())));
        await(runAsOrganization(() -> permissions.deleteRole(saved.id())));
        assertTrue(bundled(saved.id()).isEmpty());
        assertFalse(await(runAsOrganization(permissions::findRoles)).stream().anyMatch(role -> role.id().equals(saved.id())));
    }

    @Test
    public void grantsReachDownTheTreeAndStopWhenRevoked() throws Exception {
        Application application = application();
        Project project = project(application);
        UserParticipantIdentity member = member();
        Subject subject = user(member);
        Resource onApplication = new Resource(AuthzUtil.APPLICATION_TYPE, application.getId());
        Resource onProject = new Resource(ProjectService.RESOURCE_TYPE, project.getId());

        Grant grant = await(runAsOrganization(() -> permissions.grant(subject, "project.editor", onApplication)));
        assertTrue(held(member, "project_can_edit", onProject), "an editor of the application's projects edits one inside it");

        // the grant reaches the project from the application, which is where the listing says it was made
        List<Grant> reaching = await(runAsOrganization(() -> permissions.findGrants(onProject)));
        assertEquals(List.of(grant), reaching);
        AccessExplanation edits = await(runAsOrganization(() -> permissions.explain(subject, AuthzUtil.CAN_EDIT, onProject)));
        assertTrue(edits.allowed());
        assertEquals(List.of(grant), edits.through());
        AccessExplanation deletes = await(runAsOrganization(() -> permissions.explain(subject, AuthzUtil.CAN_DELETE, onProject)));
        assertFalse(deletes.allowed());
        assertTrue(deletes.through().isEmpty());

        // a resource outside the organization, and a grant revoked anywhere but where it was made, are refused
        assertThrows(ExecutionException.class, () -> await(runAsOrganization(() -> permissions.grant(
                subject, "project.editor", new Resource(ProjectService.RESOURCE_TYPE, "elsewhere-" + suffix())))));
        assertThrows(ExecutionException.class, () -> await(runAsOrganization(() -> permissions.revoke(onProject, grant.id()))));

        await(runAsOrganization(() -> permissions.revoke(onApplication, grant.id())));
        assertFalse(held(member, "project_can_edit", onProject));
        assertTrue(await(runAsOrganization(() -> permissions.findGrants(onProject))).isEmpty());
    }

    @Test
    public void groupsGrantThroughTheirMembers() throws Exception {
        Group group = await(runAsOrganization(() -> permissions.saveGroup(new Group().setName("QA " + suffix()).setDescription("tests everything"))));
        UserParticipantIdentity member = member();
        await(runAsOrganization(() -> permissions.addGroupMember(group.getId(), member.getId())));
        List<UserParticipantIdentity> members = await(runAsOrganization(() -> permissions.findGroupMembers(group.getId())));
        assertEquals(List.of(member.getId()), members.stream().map(UserParticipantIdentity::getId).toList());
        assertTrue(await(runAsOrganization(() -> permissions.findGroups(Pageable.ofSize(1000)))).getContent()
                           .stream().anyMatch(listed -> listed.getId().equals(group.getId())));

        Project project = project(application());
        Resource onProject = new Resource(ProjectService.RESOURCE_TYPE, project.getId());
        Grant grant = await(runAsOrganization(() -> permissions.grant(new Subject(SubjectKind.GROUP, group.getId()), "project.viewer", onProject)));
        assertTrue(held(member, "project_can_view", onProject), "a member views what the group was granted");
        AccessExplanation views = await(runAsOrganization(() -> permissions.explain(user(member), AuthzUtil.CAN_VIEW, onProject)));
        assertTrue(views.allowed());
        assertEquals(List.of(grant), views.through());

        await(runAsOrganization(() -> permissions.removeGroupMember(group.getId(), member.getId())));
        assertFalse(held(member, "project_can_view", onProject));

        // a group a grant holds stays until the grant is revoked
        assertThrows(ExecutionException.class, () -> await(runAsOrganization(() -> permissions.deleteGroup(group.getId()))));
        await(runAsOrganization(() -> permissions.revoke(onProject, grant.id())));
        await(runAsOrganization(() -> permissions.deleteGroup(group.getId())));
        assertFalse(await(runAsOrganization(() -> permissions.findGroups(Pageable.ofSize(1000)))).getContent()
                            .stream().anyMatch(listed -> listed.getId().equals(group.getId())));
    }

    @Test
    public void theCallerListsWhatItHolds() throws Exception {
        Application application = application();
        Project granted = project(application);
        project(application);
        UserParticipantIdentity member = member();
        await(runAsOrganization(() -> permissions.grant(user(member), "project.viewer", new Resource(ProjectService.RESOURCE_TYPE, granted.getId()))));

        DefaultOrganizationParticipant caller = new DefaultOrganizationParticipant(member.getId(), TEST_ORG_ID,
                                                                                   Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
                                                                                          ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                                                   List.of());
        assertEquals(List.of(granted.getId()), await(runAs(caller, () -> permissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzUtil.CAN_VIEW))));
    }

    private boolean held(UserParticipantIdentity member, String permission, Resource resource) throws Exception {
        return await(relationships.check(AuthzStoreService.PLATFORM, modelId,
                                         new RelationshipTuple(AuthzUtil.object(AuthzUtil.USER_TYPE, member.getId()), permission,
                                                               AuthzUtil.object(resource.type(), resource.id())),
                                         Consistency.HIGHER_CONSISTENCY));
    }

    private Set<String> bundled(String roleId) throws Exception {
        return await(relationships.read(AuthzStoreService.PLATFORM, AuthzUtil.object(AuthzUtil.ROLE_TYPE, roleId)))
                .stream().map(RelationshipTuple::relation).collect(java.util.stream.Collectors.toSet());
    }

    private Application application() throws Exception {
        return await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Permission " + suffix(), "permissions", null)));
    }

    private Project project(Application application) throws Exception {
        Project project = new Project();
        project.setName("Permission Project " + suffix());
        project.setOrganizationId(TEST_ORG_ID);
        project.setApplicationId(application.getId());
        return await(runAsOrganization(() -> projectService.createProjectIfNotExist(project)));
    }

    private UserParticipantIdentity member() throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("permission-" + suffix() + "@kinotic.test");
        user.setDisplayName("Permission User");
        user.setOrganizationId(TEST_ORG_ID);
        return await(identityService.createUser(user, "Permission-1"));
    }

    private static Subject user(UserParticipantIdentity member) {
        return new Subject(SubjectKind.USER, member.getId());
    }

    private boolean reconciledTo(String hash) throws Exception {
        AuthzStore store = await(stores.findById(AuthzStore.PLATFORM));
        ReconcileState<AuthzModelRevision> state = store.getState();
        return state.isReconciled() && state.getObserved() != null && hash.equals(state.getObserved().hash());
    }

    // Lowercase letters and digits, so it can end a slug
    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
