package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServerInfo;
import org.kinotic.domain.api.model.security.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachinePurpose;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.internal.api.services.DefaultJobService;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.model.workload.VmNode;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.system.api.workload.WorkloadStatusReport;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.system.api.workload.VmNodeRegistration;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.services.WorkloadOrchestrationService;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the assembled deploy job against stub orchestration to pin the credential contract
 * of both workloads: each authenticates with its own ORGANIZATION-scope machine identity,
 * the secret travels in {@code secrets} (persisted masked) and never in {@code environment},
 * and a redeploy rotates only the sync machine - the runtime workload keeps the secret it
 * was created with.
 */
public class ProjectDeployJobDefinitionFactoryTest {

    private static final String ORG_ID = "acme";
    private static final String APP_ID = "crm";
    private static final String PROJECT_ID = "crm-site";
    private static final String COMMIT = "c".repeat(40);

    private AnnotationConfigApplicationContext appCtx;
    private Vertx vertx;
    private DefaultJobService jobService;
    private ParticipantIdentityService identityService;
    private StubOrchestration orchestration;
    private ProjectDeployJobDefinitionFactory factory;

    @BeforeEach
    void setUp() {
        appCtx = new AnnotationConfigApplicationContext();
        appCtx.refresh();
        vertx = Vertx.vertx();
        jobService = new DefaultJobService(new InMemoryJobRunRepository(), new ObjectMapper(), vertx,
                                           () -> new ServerInfo("test-node", "deploy factory test node"));
        jobService.setApplicationContext(appCtx);

        identityService = mock(ParticipantIdentityService.class);
        when(identityService.createMachine(any())).thenAnswer(invocation -> {
            MachineParticipantIdentity machine = invocation.getArgument(0);
            return Future.succeededFuture(new MachineProvisionResult(machine, "secret-" + machine.getId()));
        });

        orchestration = new StubOrchestration();
        KinoticSystemApiProperties properties = new KinoticSystemApiProperties();
        properties.getSystemApi().getDeployment().setServerHost("server.test");
        factory = new ProjectDeployJobDefinitionFactory(orchestration.nodes,
                                                        orchestration,
                                                        (orgId, projectId) -> Future.succeededFuture(
                                                                new ProjectRepoToken("git-token",
                                                                                     Instant.now().plusSeconds(600),
                                                                                     "https://example.test/repo.git",
                                                                                     "main")),
                                                        identityService,
                                                        properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        appCtx.close();
    }

    @Test
    public void bothWorkloadsCarryTheirOwnOrganizationScopeCredentials() throws Exception {
        run(null);

        Workload sync = orchestration.deployed.get(0);
        assertEquals("project-sync-" + PROJECT_ID, sync.getName());
        assertEquals("project-deploy-" + PROJECT_ID, sync.getEnvironment().get("KINOTIC_CLIENT_ID"));
        assertEquals(ORG_ID, sync.getEnvironment().get("KINOTIC_ORGANIZATION_ID"));
        // an application id in the credentials would narrow the connection to that
        // application, and entity sync must reach the management zone
        assertFalse(sync.getEnvironment().containsKey("KINOTIC_APPLICATION_ID"));
        assertEquals("secret-project-deploy-" + PROJECT_ID, sync.getSecrets().get("KINOTIC_CLIENT_SECRET"));
        assertFalse(sync.getEnvironment().containsKey("KINOTIC_CLIENT_SECRET"));
        assertTrue(orchestration.destroyed.contains(sync.getId()));

        Workload runtime = orchestration.deployed.get(1);
        assertEquals("project-runtime-" + PROJECT_ID, runtime.getName());
        assertEquals("project-runtime-" + PROJECT_ID, runtime.getEnvironment().get("KINOTIC_CLIENT_ID"));
        assertEquals(APP_ID, runtime.getEnvironment().get("KINOTIC_PROJECT_APPLICATION_ID"));
        assertEquals("secret-project-runtime-" + PROJECT_ID, runtime.getSecrets().get("KINOTIC_CLIENT_SECRET"));
        assertFalse(runtime.getEnvironment().containsKey("KINOTIC_CLIENT_SECRET"));

        ArgumentCaptor<MachineParticipantIdentity> minted = ArgumentCaptor.forClass(MachineParticipantIdentity.class);
        verify(identityService, org.mockito.Mockito.times(2)).createMachine(minted.capture());
        for (MachineParticipantIdentity machine : minted.getAllValues()) {
            assertEquals(ORG_ID, machine.getOrganizationId());
            assertEquals(null, machine.getApplicationId());
            assertEquals(PROJECT_ID, machine.getPurposeId());
            assertEquals(machine.getPurpose().machineId(PROJECT_ID), machine.getId());
        }
        assertEquals(MachinePurpose.PROJECT_DEPLOY, minted.getAllValues().get(0).getPurpose());
        assertEquals(MachinePurpose.PROJECT_RUNTIME, minted.getAllValues().get(1).getPurpose());
    }

    @Test
    public void redeployRotatesTheSyncMachineButNotTheRuntimeMachine() throws Exception {
        run(null);
        orchestration.deployed.clear();

        ProjectDeployment existing = new ProjectDeployment()
                .setId(PROJECT_ID)
                .setOrganizationId(ORG_ID)
                .setApplicationId(APP_ID)
                .setNodeId("node-1")
                .setHostDir("/data/projects/" + PROJECT_ID)
                .setRuntimeWorkloadId("runtime-1");
        run(existing);

        // only the sync workload deploys again, with a freshly rotated deploy machine
        assertEquals(1, orchestration.deployed.size());
        assertEquals("project-sync-" + PROJECT_ID, orchestration.deployed.get(0).getName());
        ArgumentCaptor<MachineParticipantIdentity> minted = ArgumentCaptor.forClass(MachineParticipantIdentity.class);
        verify(identityService, org.mockito.Mockito.times(3)).createMachine(minted.capture());
        assertEquals("project-deploy-" + PROJECT_ID, minted.getAllValues().get(2).getId());
        assertNotEquals("project-runtime-" + PROJECT_ID, minted.getAllValues().get(2).getId());
    }

    private void run(ProjectDeployment existing) throws Exception {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setOrganizationId(ORG_ID);
        project.setApplicationId(APP_ID);
        project.setName(PROJECT_ID);

        JobDefinition definition = factory.createJobDefinition(project, existing, COMMIT);
        jobService.run(definition, JobOwner.ofApplication(ORG_ID, APP_ID))
                  .completion()
                  .toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    /**
     * Captures deployed workloads and completes them the way the deploy job expects: a
     * foreground (sync) workload finishes STOPPED with exit 0, a detached (runtime)
     * workload reports RUNNING.
     */
    private static class StubOrchestration implements WorkloadOrchestrationService {

        final List<Workload> deployed = new ArrayList<>();
        final List<String> destroyed = new ArrayList<>();
        final VmNodeOrchestrationService nodes = new StubNodes();

        @Override
        public synchronized Future<Workload> deployWorkload(Workload workload) {
            deployed.add(workload);
            workload.setId("workload-" + deployed.size());
            if (workload.isDetached()) {
                workload.setStatus(WorkloadStatus.RUNNING);
            } else {
                workload.setStatus(WorkloadStatus.STOPPED);
                workload.setExitCode(0);
            }
            return Future.succeededFuture(workload);
        }

        @Override
        public Future<Workload> restartWorkload(String workloadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> stopWorkload(String workloadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized Future<Void> destroyWorkload(String workloadId) {
            destroyed.add(workloadId);
            return Future.succeededFuture();
        }
    }

    private static class StubNodes implements VmNodeOrchestrationService {

        @Override
        public Future<VmNode> registerNode(VmNodeRegistration registration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<VmNode> heartbeat(String nodeId, List<String> problems) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> reportWorkloadStatus(String nodeId, List<WorkloadStatusReport> reports) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<Void> deregisterNode(String nodeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb) {
            VmNode node = new VmNode();
            node.setId("node-1");
            node.setWorkloadDataDir("/data");
            return Future.succeededFuture(node);
        }
    }

    /**
     * In-memory stand-in for the persistent {@link JobRunRepository}, so the engine runs
     * without Elasticsearch.
     */
    private static class InMemoryJobRunRepository extends JobRunRepository {

        private final Map<String, JobRun> runs = new LinkedHashMap<>();
        private final Map<String, TaskRecord> tasks = new LinkedHashMap<>();

        InMemoryJobRunRepository() {
            super(null, null);
        }

        @Override
        public synchronized Future<JobRun> saveRun(JobRun jobRun) {
            runs.put(jobRun.getId(), jobRun);
            return Future.succeededFuture(jobRun);
        }

        @Override
        public synchronized Future<TaskRecord> saveTask(TaskRecord taskRecord) {
            tasks.put(taskRecord.getId(), taskRecord);
            return Future.succeededFuture(taskRecord);
        }

        @Override
        public synchronized Future<JobRun> findRun(String jobRunId) {
            return Future.succeededFuture(runs.get(jobRunId));
        }

        @Override
        public synchronized Future<List<TaskRecord>> findTasks(String jobRunId) {
            return Future.succeededFuture(tasks.values().stream()
                                               .filter(record -> jobRunId.equals(record.getJobRunId()))
                                               .toList());
        }
    }

}
