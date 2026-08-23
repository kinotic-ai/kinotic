package org.kinotic.orchestrator.internal.api.services;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.orchestrator.api.config.KinoticOrchestratorProperties;
import org.kinotic.orchestrator.api.model.workload.VmNode;
import org.kinotic.orchestrator.api.model.workload.Workload;
import org.kinotic.orchestrator.api.model.workload.WorkloadStatus;
import org.kinotic.orchestrator.api.workload.WorkloadStatusReport;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Behavior of the detached contract in workload orchestration: a detached deploy returns at
 * start while a non-detached one returns at run end, exercised through the real orchestration
 * services against in-memory stubs for persistence and the node's vm-manager.
 */
public class WorkloadOrchestrationTest {

    private static final String NODE_ID = "node-1";

    private StubWorkloadService workloads;
    private StubVmNodeService nodes;
    private StubVmManagerProxy vmManager;
    private DefaultVmNodeOrchestrationService nodeOrchestration;
    private DefaultWorkloadOrchestrationService orchestration;

    @BeforeEach
    void setUp() {
        workloads = new StubWorkloadService();
        nodes = new StubVmNodeService();
        nodes.availableNode = new VmNode(NODE_ID, "node-1", "host-1");
        vmManager = new StubVmManagerProxy();
        nodeOrchestration = new DefaultVmNodeOrchestrationService(new KinoticOrchestratorProperties(),
                                                                  nodes, workloads);
        orchestration = new DefaultWorkloadOrchestrationService(nodeOrchestration, vmManager,
                                                                nodes, workloads);
    }

    @Test
    public void detachedDeployCompletesOnceStarted() throws Exception {
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));

        assertEquals(WorkloadStatus.RUNNING, deployed.getStatus());
        assertEquals(NODE_ID, deployed.getNodeId());
    }

    @Test
    public void foregroundDeployCompletesAtRunEnd() throws Exception {
        Future<Workload> run = orchestration.deployWorkload(newForegroundWorkload());
        assertFalse(run.isComplete());

        // Mid-run the node pushes its RUNNING transition while the reply stays pending
        report(vmManager.lastStarted.getId(), WorkloadStatus.RUNNING, null);
        assertFalse(run.isComplete());
        assertEquals(WorkloadStatus.RUNNING, workloads.saved.get(vmManager.lastStarted.getId()).getStatus());

        vmManager.completeRun(WorkloadStatus.STOPPED, 0);

        Workload finished = await(run);
        assertEquals(WorkloadStatus.STOPPED, finished.getStatus());
        assertEquals(0, finished.getExitCode());
        assertEquals(WorkloadStatus.STOPPED, workloads.saved.get(finished.getId()).getStatus());
        assertEquals(0, workloads.saved.get(finished.getId()).getExitCode());
    }

    @Test
    public void foregroundDeployCompletesWithFailureExitCode() throws Exception {
        Future<Workload> run = orchestration.deployWorkload(newForegroundWorkload());

        vmManager.completeRun(WorkloadStatus.FAILED, 137);

        Workload finished = await(run);
        assertEquals(WorkloadStatus.FAILED, finished.getStatus());
        assertEquals(137, finished.getExitCode());
    }

    @Test
    public void detachedReplyDoesNotClobberTerminalReport() throws Exception {
        // A short-lived detached workload: the node's terminal report is applied before the
        // start reply is processed, so the RUNNING reply must not overwrite it
        vmManager.onStart = () -> report(vmManager.lastStarted.getId(), WorkloadStatus.STOPPED, 0);

        Workload deployed = await(orchestration.deployWorkload(newWorkload()));

        assertEquals(WorkloadStatus.STOPPED, deployed.getStatus());
        assertEquals(0, deployed.getExitCode());
        assertEquals(WorkloadStatus.STOPPED, workloads.saved.get(deployed.getId()).getStatus());
    }

    @Test
    public void detachedExitCodeArrivesViaStatusReport() throws Exception {
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));

        report(deployed.getId(), WorkloadStatus.STOPPED, 0);

        Workload stored = workloads.saved.get(deployed.getId());
        assertEquals(WorkloadStatus.STOPPED, stored.getStatus());
        assertEquals(0, stored.getExitCode());
    }

    @Test
    public void foregroundRestartCompletesAtRunEnd() throws Exception {
        Future<Workload> firstRun = orchestration.deployWorkload(newForegroundWorkload());
        String workloadId = vmManager.lastStarted.getId();
        vmManager.completeRun(WorkloadStatus.STOPPED, 0);
        await(firstRun);

        Future<Workload> secondRun = orchestration.restartWorkload(workloadId);
        assertFalse(secondRun.isComplete());

        vmManager.completeRun(WorkloadStatus.STOPPED, 3);

        Workload finished = await(secondRun);
        assertEquals(WorkloadStatus.STOPPED, finished.getStatus());
        assertEquals(3, finished.getExitCode());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static Workload newWorkload() {
        return new Workload("test-workload", "alpine:latest");
    }

    private static Workload newForegroundWorkload() {
        return newWorkload().setDetached(false);
    }

    /**
     * Simulates the node pushing a status report, stamped ahead of the record's last save so
     * the report applies even when both happen within the same millisecond.
     */
    private void report(String workloadId, WorkloadStatus status, Integer exitCode) {
        WorkloadStatusReport statusReport = new WorkloadStatusReport()
                .setWorkloadId(workloadId)
                .setStatus(status)
                .setExitCode(exitCode)
                .setUpdated(System.currentTimeMillis() + 1000);
        nodeOrchestration.reportWorkloadStatus(NODE_ID, List.of(statusReport));
    }
}
