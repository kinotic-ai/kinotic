package org.kinotic.system.internal.api.deployment;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.ProjectDeployment;
import org.kinotic.domain.api.model.ProjectDeploymentStatusType;
import org.kinotic.domain.api.model.ProjectRepoToken;
import org.kinotic.domain.api.model.workload.VmNode;
import org.kinotic.domain.api.model.workload.VmNodeStatus;
import org.kinotic.domain.api.model.workload.VmNodeStatusType;
import org.kinotic.domain.api.model.workload.VolumeMount;
import org.kinotic.domain.api.model.workload.Workload;
import org.kinotic.domain.api.model.workload.WorkloadStatus;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.internal.api.grind.StubJobRunService;
import org.kinotic.system.internal.api.grind.StubTaskRecordService;
import org.kinotic.system.internal.api.services.DefaultJobService;
import org.kinotic.system.internal.api.services.DefaultVmNodeOrchestrationService;
import org.kinotic.system.internal.api.services.DefaultWorkloadOrchestrationService;
import org.kinotic.system.internal.api.services.StubVmManagerProxy;
import org.kinotic.system.internal.api.services.StubVmNodeService;
import org.kinotic.system.internal.api.services.StubWorkloadService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deploys through the real grind engine and workload orchestration over in-memory stubs:
 * a first deployment provisions the checkout on a picked node and creates the runtime
 * workload, a later one reuses the recorded node and only syncs, and a failed sync keeps
 * its workload for log inspection and records the failure.
 */
public class ProjectDeployServiceTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final String SHA = "a".repeat(40);
    private static final String DATA_DIR = "/var/lib/kinotic/workloads";

    private AnnotationConfigApplicationContext appCtx;
    private StubWorkloadService workloads;
    private StubVmNodeService nodes;
    private StubVmManagerProxy vmManager;
    private FakeProjectDeploymentRepository deployments;
    private ProjectDeployService service;

    @BeforeEach
    void setUp() {
        appCtx = new AnnotationConfigApplicationContext();
        appCtx.refresh();
        DefaultJobService jobService = new DefaultJobService(new StubJobRunService(),
                                                             new StubTaskRecordService(),
                                                             new ObjectMapper());
        jobService.setApplicationContext(appCtx);

        workloads = new StubWorkloadService();
        nodes = new StubVmNodeService();
        nodes.availableNode = onlineNode();
        nodes.saved.put(nodes.availableNode.getId(), nodes.availableNode);
        vmManager = new StubVmManagerProxy();
        DefaultVmNodeOrchestrationService nodeOrchestration =
                new DefaultVmNodeOrchestrationService(new KinoticSystemApiProperties(), nodes, workloads);
        DefaultWorkloadOrchestrationService orchestration =
                new DefaultWorkloadOrchestrationService(nodeOrchestration, vmManager, nodes, workloads);

        deployments = new FakeProjectDeploymentRepository();
        Project project = new Project();
        project.setId(PROJECT);
        project.setOrganizationId(ORG);
        project.setApplicationId("app-1");
        project.setName("proj");
        KinoticSystemApiProperties properties = new KinoticSystemApiProperties();
        properties.getOrchestrator().getDeployment()
                  .setServerHost("gateway.example")
                  .setServerPort(58503);

        service = new ProjectDeployService(jobService,
                                           nodeOrchestration,
                                           orchestration,
                                           deployments,
                                           new FakeProjectRepository(project),
                                           (organizationId, projectId) -> Future.succeededFuture(
                                                   new ProjectRepoToken("tok-123",
                                                                        Instant.now().plusSeconds(3600),
                                                                        "https://github.com/acme/proj.git",
                                                                        "main")),
                                           properties);
    }

    @AfterEach
    void tearDown() {
        appCtx.close();
    }

    @Test
    public void firstDeploymentProvisionsCheckoutAndRuntimeWorkload() throws Exception {
        Future<Void> run = service.deployProject(ORG, PROJECT, SHA);

        // The sync workload holds the node's reply open until its run ends
        awaitUntil(() -> vmManager.pendingReply != null);
        Workload sync = vmManager.lastStarted;
        assertFalse(sync.isDetached());
        assertEquals("node-1", sync.getNodeId());
        assertEquals(List.of("bun", "src/sync.ts"), workloads.saved.get(sync.getId()).getEntrypoint());
        assertEquals("https://github.com/acme/proj.git", sync.getEnvironment().get("GIT_CLONE_URL"));
        assertEquals(SHA, sync.getEnvironment().get("GIT_REF"));
        assertEquals("gateway.example", sync.getEnvironment().get("KINOTIC_SERVER_HOST"));
        // The node receives the real token while the persisted record only holds a mask
        assertEquals("tok-123", sync.getSecrets().get("GIT_TOKEN"));
        assertEquals("<redacted>", workloads.saved.get(sync.getId()).getSecrets().get("GIT_TOKEN"));
        VolumeMount syncMount = workloads.saved.get(sync.getId()).getVolumeMounts().getFirst();
        assertEquals(DATA_DIR + "/projects/" + PROJECT, syncMount.getHostPath());
        assertEquals("/workspace", syncMount.getGuestPath());
        assertFalse(syncMount.isReadOnly());

        ProjectDeployment deploying = deployments.saved.get(PROJECT);
        assertEquals(ProjectDeploymentStatusType.DEPLOYING, deploying.getStatus().type());
        assertNotNull(deploying.getLastJobRunId());

        vmManager.completeRun(WorkloadStatus.STOPPED, 0);
        await(run);

        // Clean sync destroyed its workload; the runtime workload serves the checkout read-only
        assertNull(workloads.saved.get(sync.getId()));
        Workload runtime = vmManager.lastStarted;
        assertTrue(runtime.isDetached());
        assertEquals("node-1", runtime.getNodeId());
        VolumeMount runtimeMount = workloads.saved.get(runtime.getId()).getVolumeMounts().getFirst();
        assertEquals(DATA_DIR + "/projects/" + PROJECT, runtimeMount.getHostPath());
        assertEquals("/app", runtimeMount.getGuestPath());
        assertTrue(runtimeMount.isReadOnly());

        ProjectDeployment recorded = deployments.saved.get(PROJECT);
        assertEquals(ProjectDeploymentStatusType.RUNNING, recorded.getStatus().type());
        assertEquals(SHA, recorded.getCommitSha());
        assertEquals("node-1", recorded.getNodeId());
        assertEquals(DATA_DIR + "/projects/" + PROJECT, recorded.getHostDir());
        assertEquals(runtime.getId(), recorded.getRuntimeWorkloadId());
    }

    @Test
    public void laterDeploymentReusesTheRecordedNodeAndOnlySyncs() throws Exception {
        deployments.saved.put(PROJECT, new ProjectDeployment()
                .setId(PROJECT)
                .setOrganizationId(ORG)
                .setApplicationId("app-1")
                .setNodeId("node-1")
                .setHostDir(DATA_DIR + "/projects/" + PROJECT)
                .setRuntimeWorkloadId("runtime-1")
                .setCommitSha("b".repeat(40)));
        // A pinned deploy validates against the registered node, not findAvailableNode
        nodes.availableNode = null;

        Future<Void> run = service.deployProject(ORG, PROJECT, SHA);
        awaitUntil(() -> vmManager.pendingReply != null);
        vmManager.completeRun(WorkloadStatus.STOPPED, 0);
        await(run);

        // Only the sync workload ran; the supervisor of runtime-1 picks up the sentinel
        assertEquals(1, vmManager.started.size());
        ProjectDeployment recorded = deployments.saved.get(PROJECT);
        assertEquals(ProjectDeploymentStatusType.RUNNING, recorded.getStatus().type());
        assertEquals(SHA, recorded.getCommitSha());
        assertEquals("runtime-1", recorded.getRuntimeWorkloadId());
    }

    @Test
    public void failedSyncKeepsItsWorkloadAndRecordsTheFailure() throws Exception {
        Future<Void> run = service.deployProject(ORG, PROJECT, SHA);
        awaitUntil(() -> vmManager.pendingReply != null);
        String syncWorkloadId = vmManager.lastStarted.getId();

        vmManager.completeRun(WorkloadStatus.FAILED, 1);
        awaitUntil(run::isComplete);

        assertTrue(run.failed());
        // The failed workload stays inspectable and no runtime workload was created
        assertNotNull(workloads.saved.get(syncWorkloadId));
        assertEquals(1, vmManager.started.size());
        ProjectDeployment recorded = deployments.saved.get(PROJECT);
        assertEquals(ProjectDeploymentStatusType.FAILED, recorded.getStatus().type());
        assertTrue(recorded.getStatus().message().contains(syncWorkloadId));
        assertNull(recorded.getCommitSha());
    }

    private static VmNode onlineNode() {
        VmNode node = new VmNode("node-1", "node-1", "host-1");
        node.setStatus(new VmNodeStatus().setType(VmNodeStatusType.ONLINE));
        node.setTotalCpus(8);
        node.setTotalMemoryMb(16384);
        node.setTotalDiskMb(102400);
        node.setWorkloadDataDir(DATA_DIR);
        return node;
    }

    private static void await(Future<Void> future) throws Exception {
        awaitUntil(future::isComplete);
        future.toCompletionStage().toCompletableFuture().get(1, TimeUnit.SECONDS);
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < deadline, "condition not met within 15s");
            Thread.sleep(10);
        }
    }

}
