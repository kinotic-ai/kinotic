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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the listings show a member what it may see and nothing else: an application the member reads or one
 * containing a project or an entity definition it reads, and the projects it reads or those containing an
 * entity definition it reads. What is hidden is absent from a listing and answered as not found by id, and the
 * organization's administrator sees everything.
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
    public void aMemberWithNoGrantSeesNothing() throws Exception {
        Application x = application();
        project(x);
        OrganizationParticipant caller = participant(member(TEST_ORG_ID, "visible").getId());

        assertTrue(ids(await(runAs(caller, () -> applicationService.findAll(FIRST_PAGE)))).isEmpty());
        assertEquals(0L, await(runAs(caller, applicationService::count)));
        assertNull(await(runAs(caller, () -> applicationService.findById(x.getId()))));
        assertTrue(ids(await(runAs(caller, () -> projectService.findAll(FIRST_PAGE)))).isEmpty());
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
