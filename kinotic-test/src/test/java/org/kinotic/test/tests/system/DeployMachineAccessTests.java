package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.security.identity.MachineKind;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.services.EntityDefinitionService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.system.internal.api.services.deployment.ProjectDeployIdentityService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.sample.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies what the machines a project's deployment provisions are admitted to, through the authorizer the gateway
 * calls: the sync machine to every function {@code kinotic sync --publish} and the workload runner call, finding,
 * creating, saving and publishing the application's entity definitions and their named queries, running the
 * project's migrations and recording its artifacts; a runtime machine to registering the application's services and
 * to every operation on the rows of the application's definitions; and a machine the project's deployment recorded
 * before, whose credentials are issued again, to the same.
 */
@SpringBootTest
public class DeployMachineAccessTests extends KinoticTestBase {

    private static final String ENTITY_DEFINITION_SERVICE = "management-api~org.kinotic.management.api.services.EntityDefinitionService";
    private static final String NAMED_QUERIES_SERVICE = "management-api~org.kinotic.management.api.services.NamedQueriesDefinitionService";
    private static final String MIGRATION_SERVICE = "management-api~org.kinotic.management.api.services.MigrationService";
    private static final String ARTIFACT_SERVICE = "management-api~org.kinotic.management.api.services.deployment.ProjectArtifactService";
    private static final String DIRECTORY_SERVICE = "app-api~org.kinotic.app.api.services.ServiceDirectoryService";
    private static final String ENTITIES_SERVICE = "app-api~org.kinotic.persistence.api.services.JsonEntitiesRepository";

    @Autowired
    private ProjectDeployIdentityService deployIdentities;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EntityDefinitionService entityDefinitionService;

    @Autowired
    private ProjectDeploymentRepository projectDeployments;

    @Autowired
    private MicroserviceDeploymentRepository microservices;

    @Autowired
    private ParticipantIdentityService identities;

    @Autowired
    private TestDataService testData;

    private final List<String> machineIds = new ArrayList<>();
    private final List<String> microserviceIds = new ArrayList<>();
    private final List<String> projectIds = new ArrayList<>();

    @AfterEach
    public void removeCreatedRecords() throws Exception {
        for (String id : machineIds) {
            await(runAsOrganization(() -> identities.deleteById(id)));
        }
        for (String id : microserviceIds) {
            await(microservices.deleteByIdSync(id));
        }
        for (String id : projectIds) {
            await(projectDeployments.deleteByIdSync(id, TEST_ORG_ID));
            await(runAsOrganization(() -> projectService.deleteById(id)));
        }
    }

    @Test
    public void theSyncMachineIsAdmittedToEverythingASyncDoes() throws Exception {
        Project project = deployedProject();
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();

        Participant sync = machine(issued(deployIdentities.issueSyncCredentials(project)));

        assertSyncs(sync, project, person);
    }

    @Test
    public void aSyncMachineTheDeploymentRecordedBeforeIsAdmittedOnceItsCredentialsAreIssuedAgain() throws Exception {
        Project project = deployedProject();
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        // a machine the deployment recorded but never bound, as one provisioned before its workload needed more is
        MachineParticipantIdentity unbound = new MachineParticipantIdentity();
        unbound.setMachineKind(MachineKind.PROJECT_SYNC)
               .setDisplayName(project.getName() + " deploy sync")
               .setOrganizationId(TEST_ORG_ID);
        String recorded = issued(identities.createMachine(unbound)).getId();
        await(projectDeployments.recordSyncMachine(project.getId(), TEST_ORG_ID, recorded));
        assertFalse(admitted(ENTITY_DEFINITION_SERVICE, "create", machine(recorded), List.of(Map.of("applicationId", TEST_APP_ID))));

        MachineParticipantIdentity reissued = issued(deployIdentities.issueSyncCredentials(project));

        assertEquals(recorded, reissued.getId(), "the recorded machine is issued again rather than replaced");
        assertSyncs(machine(recorded), project, person);
    }

    @Test
    public void aRuntimeMachineIsAdmittedToRegisteringItsApplicationsServicesAndToTheirRows() throws Exception {
        Project project = deployedProject();
        EntityDefinition person = await(runAsOrganization(() -> testData.createPersonEntityDefinitionIfNotExists())).getLeft();
        MicroserviceDeployment deployment = microservice(project);

        Participant runtime = machine(issued(deployIdentities.issueRuntimeCredentials(project, deployment)));

        assertAdmitted(DIRECTORY_SERVICE, "register", runtime, List.of(Map.of("applicationId", TEST_APP_ID)));
        for (String function : List.of("save", "update", "findById", "search", "deleteById")) {
            assertAdmitted(ENTITIES_SERVICE, function, runtime, List.of(person.getId()));
        }
    }

    // The engine answers from its caches, which lag a binding by a moment, so each answer is awaited
    private void assertSyncs(Participant sync, Project project, EntityDefinition person) throws Exception {
        assertTrue(awaitUntil(() -> await(runAs(sync, () -> projectService.findById(project.getId()))) != null),
                   "the sync machine cannot find its project");
        assertTrue(awaitUntil(() -> await(runAs(sync, () -> entityDefinitionService.findById(person.getId()))) != null),
                   "the sync machine cannot find the application's definition");
        assertAdmitted(ENTITY_DEFINITION_SERVICE, "create", sync, List.of(Map.of("applicationId", TEST_APP_ID)));
        assertAdmitted(ENTITY_DEFINITION_SERVICE, "save", sync, List.of(Map.of("id", person.getId())));
        assertAdmitted(ENTITY_DEFINITION_SERVICE, "publish", sync, List.of(person.getId()));
        assertAdmitted(NAMED_QUERIES_SERVICE, "save", sync, List.of(Map.of("id", person.getId())));
        assertAdmitted(MIGRATION_SERVICE, "getLastAppliedMigrationVersion", sync, List.of(project.getId()));
        assertAdmitted(MIGRATION_SERVICE, "executeMigrations", sync, List.of(Map.of("projectId", project.getId())));
        assertAdmitted(ARTIFACT_SERVICE, "recordArtifacts", sync, List.of(project.getId()));
    }

    private void assertAdmitted(String service, String function, Participant caller, Object arguments) throws Exception {
        assertTrue(awaitUntil(() -> admitted(service, function, caller, arguments)), "refused " + function + " of " + service);
    }

    // A project of the sample application with a deployment record, whose intent names no commit so the project's
    // worker runs no deploy job issuing the project's credentials beside the test
    private Project deployedProject() throws Exception {
        Project project = new Project();
        project.setOrganizationId(TEST_ORG_ID);
        project.setApplicationId(TEST_APP_ID);
        project.setName("Deploy Access " + UUID.randomUUID().toString().substring(0, 8));
        Project created = await(runAsOrganization(() -> projectService.createProjectIfNotExist(project)));
        projectIds.add(created.getId());
        ProjectDeployment upsert = new ProjectDeployment().setId(created.getId())
                                                          .setOrganizationId(TEST_ORG_ID)
                                                          .setApplicationId(TEST_APP_ID)
                                                          .setCreated(new Date())
                                                          .setUpdated(new Date());
        await(projectDeployments.updateDesired(created.getId(), TEST_ORG_ID, new DeploymentState(DeploymentStatusType.RUNNING, null),
                                               upsert, "deploy access test"));
        return created;
    }

    private MicroserviceDeployment microservice(Project project) throws Exception {
        String id = project.getId() + "-api";
        MicroserviceDeployment upsert = new MicroserviceDeployment().setId(id)
                                                                    .setOrganizationId(TEST_ORG_ID)
                                                                    .setApplicationId(TEST_APP_ID)
                                                                    .setProjectId(project.getId())
                                                                    .setName("api")
                                                                    .setCreated(new Date())
                                                                    .setUpdated(new Date());
        microserviceIds.add(id);
        return await(microservices.updateDesired(id, new DeploymentState(DeploymentStatusType.DEPLOYED, null), upsert, "deploy access test"));
    }

    private MachineParticipantIdentity issued(Future<MachineProvisionResult> issue) throws Exception {
        MachineParticipantIdentity ret = await(issue).machine();
        if (!machineIds.contains(ret.getId())) {
            machineIds.add(ret.getId());
        }
        return ret;
    }

    private static Participant machine(MachineParticipantIdentity identity) {
        return machine(identity.getId());
    }

    private static Participant machine(String id) {
        return new DefaultOrganizationParticipant(id, TEST_ORG_ID,
                                                  Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_MACHINE),
                                                  List.of());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
