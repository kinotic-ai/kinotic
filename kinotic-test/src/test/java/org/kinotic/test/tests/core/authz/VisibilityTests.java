package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.ReconcileState;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.model.persistence.idl.decorators.TenantIdDecorator;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.StringC3Type;
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
 * Verifies the listings show a member what it may see and nothing else: an application the member reads or one
 * containing a project or an entity definition it reads, and the projects it reads or those containing an
 * entity definition it reads, where a project contains the definitions of its own application that name it, and a
 * definition names a project of its application that its author may edit. What is hidden is absent from a listing
 * and answered as not found by id, a project another application holds is refused to a create naming its id, and
 * the organization's administrator sees everything.
 */
@SpringBootTest
public class VisibilityTests extends KinoticTestBase {

    private static final Pageable FIRST_PAGE = Pageable.create(0, 1000, Sort.by("id"));
    private static final String TENANT_ID_FIELD = "tenantId";

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EntityDefinitionService entityDefinitionService;

    @Autowired
    private PermissionService permissions;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private TestDataService testDataService;

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
    public void aMemberSeesTheApplicationsAndProjectsItHoldsSomethingIn() throws Exception {
        Application x = application();
        Project a = project(x);
        Project b = project(x);
        Application y = application();
        project(y);
        OrganizationParticipantIdentity sally = member(TEST_ORG_ID, "visible");
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "project.editor",
                                                        new Resource(ProjectService.RESOURCE_TYPE, a.getId()))));
        OrganizationParticipant caller = participant(sally.getId());

        // the application is visible for the project inside it, the other application is not
        assertTrue(awaitUntil(() -> ids(await(runAs(caller, () -> applicationService.findAll(FIRST_PAGE)))).contains(x.getId())),
                   "the application never became visible");
        List<String> applications = ids(await(runAs(caller, () -> applicationService.findAll(FIRST_PAGE))));
        assertEquals(List.of(x.getId()), applications);
        assertEquals(1L, await(runAs(caller, applicationService::count)));
        assertEquals(List.of(x.getId()), ids(await(runAs(caller, () -> applicationService.search(x.getName(), FIRST_PAGE)))));
        assertNotNull(await(runAs(caller, () -> applicationService.findById(x.getId()))));
        assertNull(await(runAs(caller, () -> applicationService.findById(y.getId()))));

        // inside the application, only the project the grant reaches
        assertEquals(List.of(a.getId()), ids(await(runAs(caller, () -> projectService.findAllForApplication(x.getId(), FIRST_PAGE)))));
        assertEquals(1L, await(runAs(caller, () -> projectService.countForApplication(x.getId()))));
        assertEquals(List.of(a.getId()), ids(await(runAs(caller, () -> projectService.findAll(FIRST_PAGE)))));
        assertEquals(1L, await(runAs(caller, projectService::count)));
        assertEquals(List.of(a.getId()), ids(await(runAs(caller, () -> projectService.search(a.getName(), FIRST_PAGE)))));
        assertTrue(ids(await(runAs(caller, () -> projectService.search(b.getName(), FIRST_PAGE)))).isEmpty());

        // the administrator sees everything
        assertTrue(ids(await(runAsOrganization(() -> applicationService.findAll(FIRST_PAGE)))).containsAll(List.of(x.getId(), y.getId())));
        assertTrue(ids(await(runAsOrganization(() -> projectService.findAllForApplication(x.getId(), FIRST_PAGE)))).containsAll(List.of(a.getId(), b.getId())));
        assertNotNull(await(runAsOrganization(() -> applicationService.findById(y.getId()))));
    }

    @Test
    public void aGrantOnAnEntityDefinitionReachesUpToItsProjectAndApplication() throws Exception {
        Application x = application();
        Project a = project(x);
        Project b = project(x);
        EntityDefinition definition = await(runAsOrganization(() -> entityDefinitionService.create(definition(x.getId(), a.getId()))));
        OrganizationParticipantIdentity sally = member(TEST_ORG_ID, "visible");
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "entity_definition.viewer",
                                                        new Resource(EntityDefinitionService.RESOURCE_TYPE, definition.getId()))));
        OrganizationParticipant caller = participant(sally.getId());

        assertTrue(awaitUntil(() -> ids(await(runAs(caller, () -> applicationService.findAll(FIRST_PAGE)))).contains(x.getId())),
                   "the application never became visible");
        assertEquals(List.of(a.getId()), ids(await(runAs(caller, () -> projectService.findAllForApplication(x.getId(), FIRST_PAGE)))));
        assertFalse(ids(await(runAs(caller, () -> projectService.findAll(FIRST_PAGE)))).contains(b.getId()));
    }

    @Test
    public void aMemberListsTheEntityDefinitionsItMayRead() throws Exception {
        Application x = application();
        Project a = project(x);
        Project b = project(x);
        EntityDefinition granted = await(runAsOrganization(() -> entityDefinitionService.create(definition(x.getId(), a.getId()))));
        EntityDefinition beside = await(runAsOrganization(() -> entityDefinitionService.create(definition(x.getId(), a.getId()))));
        EntityDefinition elsewhere = await(runAsOrganization(() -> entityDefinitionService.create(definition(x.getId(), b.getId()))));
        OrganizationParticipantIdentity sally = member(TEST_ORG_ID, "visible");
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "entity_definition.viewer",
                                                        new Resource(EntityDefinitionService.RESOURCE_TYPE, granted.getId()))));
        OrganizationParticipant caller = participant(sally.getId());

        // the one definition the grant is made on, in every listing that holds it
        assertTrue(awaitUntil(() -> ids(await(runAs(caller, () -> entityDefinitionService.findAllForProject(a.getId(), FIRST_PAGE)))).contains(granted.getId())),
                   "the definition never became readable");
        assertEquals(List.of(granted.getId()), ids(await(runAs(caller, () -> entityDefinitionService.findAllForProject(a.getId(), FIRST_PAGE)))));
        assertEquals(1L, await(runAs(caller, () -> entityDefinitionService.countForProject(a.getId()))));
        assertTrue(ids(await(runAs(caller, () -> entityDefinitionService.findAllForProject(b.getId(), FIRST_PAGE)))).isEmpty());
        assertEquals(0L, await(runAs(caller, () -> entityDefinitionService.countForProject(b.getId()))));
        assertEquals(List.of(granted.getId()), ids(await(runAs(caller, () -> entityDefinitionService.findAllForApplication(x.getId(), FIRST_PAGE)))));
        assertEquals(1L, await(runAs(caller, () -> entityDefinitionService.countForApplication(x.getId()))));
        assertEquals(List.of(granted.getId()), ids(await(runAs(caller, () -> entityDefinitionService.findAll(FIRST_PAGE)))));
        assertEquals(1L, await(runAs(caller, entityDefinitionService::count)));
        assertEquals(List.of(granted.getId()), ids(await(runAs(caller, () -> entityDefinitionService.search(granted.getName(), FIRST_PAGE)))));
        assertTrue(ids(await(runAs(caller, () -> entityDefinitionService.search(beside.getName(), FIRST_PAGE)))).isEmpty());

        // a grant on the application reaches every definition in it
        OrganizationParticipantIdentity ravi = member(TEST_ORG_ID, "visible");
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, ravi.getId()), "entity_definition.viewer",
                                                        new Resource(AuthzConstants.APPLICATION_TYPE, x.getId()))));
        OrganizationParticipant reader = participant(ravi.getId());
        assertTrue(awaitUntil(() -> await(runAs(reader, () -> entityDefinitionService.countForApplication(x.getId()))) == 3L),
                   "the application's definitions never became readable");
        assertEquals(Set.of(granted.getId(), beside.getId()),
                     Set.copyOf(ids(await(runAs(reader, () -> entityDefinitionService.findAllForProject(a.getId(), FIRST_PAGE))))));
        assertEquals(List.of(elsewhere.getId()), ids(await(runAs(reader, () -> entityDefinitionService.findAllForProject(b.getId(), FIRST_PAGE)))));
        assertEquals(3L, await(runAs(reader, entityDefinitionService::count)));

        // the administrator lists every definition
        assertEquals(2L, await(runAsOrganization(() -> entityDefinitionService.countForProject(a.getId()))));
        assertEquals(3L, await(runAsOrganization(() -> entityDefinitionService.countForApplication(x.getId()))));
    }

    @Test
    public void anEntityDefinitionNamesAProjectOfItsApplicationThatItsAuthorMayEdit() throws Exception {
        Application x = application();
        Project a = project(x);
        Application y = application();
        Project other = project(y);
        String refusal = "No project of application " + x.getId() + " that the caller may edit has id ";

        // a project nobody stores, and one another application holds
        for (String projectId : List.of(a.getId() + "-missing", other.getId())) {
            ExecutionException refused = assertThrows(ExecutionException.class,
                                                      () -> await(runAsOrganization(() -> entityDefinitionService.create(definition(x.getId(), projectId)))));
            assertInstanceOf(IllegalArgumentException.class, refused.getCause());
            assertEquals(refusal + projectId, refused.getCause().getMessage());
        }

        // a project of the application the author holds nothing on is refused in the same words
        OrganizationParticipantIdentity sally = member(TEST_ORG_ID, "visible");
        OrganizationParticipant caller = participant(sally.getId());
        ExecutionException refused = assertThrows(ExecutionException.class,
                                                  () -> await(runAs(caller, () -> entityDefinitionService.create(definition(x.getId(), a.getId())))));
        assertInstanceOf(IllegalArgumentException.class, refused.getCause());
        assertEquals(refusal + a.getId(), refused.getCause().getMessage());
        assertTrue(await(runAsOrganization(() -> entityDefinitionService.findAllForProject(a.getId(), FIRST_PAGE))).getContent().isEmpty());

        // reading the project is not editing it
        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "project.viewer",
                                                        new Resource(ProjectService.RESOURCE_TYPE, a.getId()))));
        assertTrue(awaitUntil(() -> await(runAs(caller, () -> projectService.findById(a.getId()))) != null), "the project never became readable");
        assertThrows(ExecutionException.class,
                     () -> await(runAs(caller, () -> entityDefinitionService.create(definition(x.getId(), a.getId())))));

        await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "project.editor",
                                                        new Resource(ProjectService.RESOURCE_TYPE, a.getId()))));
        assertTrue(awaitUntil(() -> created(caller, definition(x.getId(), a.getId()))), "the project's editor was never allowed to name it");
    }

    @Test
    public void aProjectCreatedAgainInAnotherApplicationIsNotReachedThroughADefinitionNamingItsId() throws Exception {
        Application x = application();
        Project a = project(x);
        Project replaced = project(x);
        Application y = application();
        EntityDefinition inside = await(runAsOrganization(() -> entityDefinitionService.create(definition(x.getId(), a.getId()))));
        EntityDefinition naming = await(runAsOrganization(() -> entityDefinitionService.create(definition(x.getId(), replaced.getId()))));
        // the project the definition names is deleted, and its id taken by a project of y
        await(runAsOrganization(() -> projectService.deleteByIdSync(replaced.getId())));
        Project hidden = new Project();
        hidden.setId(replaced.getId());
        hidden.setName(replaced.getName());
        hidden.setOrganizationId(TEST_ORG_ID);
        hidden.setApplicationId(y.getId());
        await(runAsOrganization(() -> projectService.createProjectIfNotExist(hidden)));
        OrganizationParticipantIdentity sally = member(TEST_ORG_ID, "visible");
        for (EntityDefinition definition : List.of(inside, naming)) {
            await(runAsOrganization(() -> permissions.grant(new Subject(SubjectType.USER, sally.getId()), "entity_definition.viewer",
                                                            new Resource(EntityDefinitionService.RESOURCE_TYPE, definition.getId()))));
        }
        OrganizationParticipant caller = participant(sally.getId());

        assertTrue(awaitUntil(() -> ids(await(runAs(caller, () -> projectService.findAll(FIRST_PAGE)))).contains(a.getId())),
                   "the definition's project never became visible");
        assertEquals(List.of(a.getId()), ids(await(runAs(caller, () -> projectService.findAll(FIRST_PAGE)))));
        assertEquals(1L, await(runAs(caller, projectService::count)));
        assertNull(await(runAs(caller, () -> projectService.findById(hidden.getId()))));
        assertTrue(ids(await(runAs(caller, () -> projectService.findAllForApplication(y.getId(), FIRST_PAGE)))).isEmpty());
        assertNull(await(runAs(caller, () -> applicationService.findById(y.getId()))));
    }

    @Test
    public void aProjectOfAnotherApplicationIsNotReturnedAsTheExistingOne() throws Exception {
        Application x = application();
        Application y = application();
        Project held = project(y);
        Project requested = new Project();
        requested.setId(held.getId());
        requested.setName(held.getName());
        requested.setOrganizationId(TEST_ORG_ID);
        requested.setApplicationId(x.getId());

        ExecutionException refused = assertThrows(ExecutionException.class,
                                                  () -> await(runAsOrganization(() -> projectService.createProjectIfNotExist(requested))));
        assertInstanceOf(IllegalArgumentException.class, refused.getCause());

        // the application holding the id is answered with its project
        Project again = new Project();
        again.setId(held.getId());
        again.setName(held.getName());
        again.setOrganizationId(TEST_ORG_ID);
        again.setApplicationId(y.getId());
        assertEquals(held.getId(), await(runAsOrganization(() -> projectService.createProjectIfNotExist(again))).getId());
    }

    @Test
    public void aMemberWithNoGrantSeesNothing() throws Exception {
        Application x = application();
        project(x);
        OrganizationParticipant caller = participant(member(TEST_ORG_ID, "visible").getId());

        assertTrue(ids(await(runAs(caller, () -> applicationService.findAll(FIRST_PAGE)))).isEmpty());
        assertEquals(0L, await(runAs(caller, applicationService::count)));
        assertNull(await(runAs(caller, () -> applicationService.findById(x.getId()))));
        assertTrue(ids(await(runAs(caller, () -> projectService.findAll(FIRST_PAGE)))).isEmpty());
    }

    private boolean created(OrganizationParticipant caller, EntityDefinition definition) {
        boolean ret;
        try {
            ret = await(runAs(caller, () -> entityDefinitionService.create(definition))) != null;
        } catch (Exception e) {
            ret = false;
        }
        return ret;
    }

    private static List<String> ids(Page<? extends Identifiable<String>> page) {
        return page.getContent().stream().map(Identifiable::getId).toList();
    }

    private static OrganizationParticipant participant(String id) {
        return new DefaultOrganizationParticipant(id, TEST_ORG_ID,
                                                  Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                  List.of());
    }

    private Application application() throws Exception {
        return await(runAsOrganization(() -> applicationService.createApplicationIfNotExist("Visible " + suffix(), "visibility", null)));
    }

    private Project project(Application application) throws Exception {
        Project project = new Project();
        project.setName("Visible Project " + suffix());
        project.setOrganizationId(TEST_ORG_ID);
        project.setApplicationId(application.getId());
        return await(runAsOrganization(() -> projectService.createProjectIfNotExist(project)));
    }

    private EntityDefinition definition(String applicationId, String projectId) {
        ObjectC3Type schema = testDataService.createPersonSchema(MultiTenancyType.SHARED)
                                             .addProperty(TENANT_ID_FIELD, new StringC3Type(), List.of(new TenantIdDecorator()));
        EntityDefinition definition = new EntityDefinition();
        definition.setName("VisiblePerson" + suffix());
        definition.setOrganizationId(TEST_ORG_ID);
        definition.setApplicationId(applicationId);
        definition.setProjectId(projectId);
        definition.setDescription("A person the visibility test defines");
        definition.setSchema(schema);
        return definition;
    }


    private boolean reconciledTo(String hash) throws Exception {
        AuthzStore store = await(stores.findById(AuthzStore.SYSTEM));
        ReconcileState<AuthzModelRevision> state = store.getState();
        return state.isReconciled() && state.getObserved() != null && hash.equals(state.getObserved().hash());
    }
}
