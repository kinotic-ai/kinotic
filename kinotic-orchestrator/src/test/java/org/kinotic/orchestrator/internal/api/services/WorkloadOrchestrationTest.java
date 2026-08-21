package org.kinotic.orchestrator.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.orchestrator.api.config.KinoticOrchestratorProperties;
import org.kinotic.orchestrator.api.model.workload.VmNode;
import org.kinotic.orchestrator.api.model.workload.Workload;
import org.kinotic.orchestrator.api.model.workload.WorkloadStatus;
import org.kinotic.orchestrator.api.workload.WorkloadStatusReport;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Behavior of deploy-and-return vs run-to-completion workload orchestration, exercised through
 * the real orchestration services and completion tracker against in-memory stubs for
 * persistence and the node's vm-manager.
 */
public class WorkloadOrchestrationTest {

    private static final String NODE_ID = "node-1";

    private Vertx vertx;
    private StubWorkloadService workloads;
    private StubVmNodeService nodes;
    private StubVmManagerProxy vmManager;
    private DefaultVmNodeOrchestrationService nodeOrchestration;
    private DefaultWorkloadOrchestrationService orchestration;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        workloads = new StubWorkloadService();
        nodes = new StubVmNodeService();
        nodes.availableNode = new VmNode(NODE_ID, "node-1", "host-1");
        vmManager = new StubVmManagerProxy();
        WorkloadCompletionTracker tracker = new WorkloadCompletionTracker();
        nodeOrchestration = new DefaultVmNodeOrchestrationService(new KinoticOrchestratorProperties(),
                                                                  nodes, workloads, tracker);
        orchestration = new DefaultWorkloadOrchestrationService(nodeOrchestration, vmManager,
                                                                nodes, workloads, tracker, vertx);
    }

    @AfterEach
    void tearDown() throws Exception {
        await(vertx.close());
    }

    @Test
    public void deployCompletesOnceStarted() throws Exception {
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));

        assertEquals(WorkloadStatus.RUNNING, deployed.getStatus());
        assertEquals(NODE_ID, deployed.getNodeId());
    }

    @Test
    public void runWaitsForTerminalReport() throws Exception {
        Future<Workload> run = orchestration.runWorkload(newWorkload());
        assertFalse(run.isComplete());
        assertEquals(WorkloadStatus.RUNNING, workloads.saved.get(vmManager.lastStarted.getId()).getStatus());

        report(vmManager.lastStarted.getId(), WorkloadStatus.STOPPED, 0);

        Workload finished = await(run);
        assertEquals(WorkloadStatus.STOPPED, finished.getStatus());
        assertEquals(0, finished.getExitCode());
    }

    @Test
    public void runCompletesWithFailureExitCode() throws Exception {
        Future<Workload> run = orchestration.runWorkload(newWorkload());

        report(vmManager.lastStarted.getId(), WorkloadStatus.FAILED, 137);

        Workload finished = await(run);
        assertEquals(WorkloadStatus.FAILED, finished.getStatus());
        assertEquals(137, finished.getExitCode());
    }

    @Test
    public void runCompletesWhenReportOutrunsStartReply() throws Exception {
        // The node's terminal report lands before the start reply is processed, so the waiter
        // registers after the run has already ended
        vmManager.onStart = () -> report(vmManager.lastStarted.getId(), WorkloadStatus.STOPPED, 0);

        Workload finished = await(orchestration.runWorkload(newWorkload()));

        assertEquals(WorkloadStatus.STOPPED, finished.getStatus());
        assertEquals(0, finished.getExitCode());
        // The start reply must not have promoted the already-ended run back to RUNNING
        assertEquals(WorkloadStatus.STOPPED, workloads.saved.get(finished.getId()).getStatus());
    }

    @Test
    public void stopSettlesRunWaiter() throws Exception {
        Future<Workload> run = orchestration.runWorkload(newWorkload());

        await(orchestration.stopWorkload(vmManager.lastStarted.getId()));

        assertEquals(WorkloadStatus.STOPPED, await(run).getStatus());
    }

    @Test
    public void destroyFailsRunWaiter() throws Exception {
        Future<Workload> run = orchestration.runWorkload(newWorkload());

        await(orchestration.destroyWorkload(vmManager.lastStarted.getId()));

        ExecutionException error = assertThrows(ExecutionException.class,
                                                () -> run.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static Workload newWorkload() {
        return new Workload("test-workload", "alpine:latest");
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
