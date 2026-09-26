package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.ServiceRegistry;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeState;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.system.api.services.workload.WorkloadOrchestrationService;
import org.kinotic.system.api.services.workload.VmManagerProxy;
import org.kinotic.system.api.model.workload.VmNodeRegistration;
import org.kinotic.system.api.model.workload.WorkloadStatusReport;
import org.kinotic.system.internal.api.repositories.VmNodeRepository;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.kinotic.test.support.system.StubVmManager;
import org.kinotic.test.support.system.VmManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workload orchestration end to end: the real orchestration services, the scripted writes against
 * Elasticsearch, and a stand-in vm-manager reached over the platform's RPC at each node's address.
 * The node's worker is called directly where the test asserts its answer, as the reconcile master
 * calls it; where the outcome is a deletion, the master running in this context is left to finalize
 * it and the test waits for the record to go.
 */
@SpringBootTest
public class WorkloadOrchestrationTests extends KinoticTestBase {

    private static final String NODE_ID = "node-1";
    private static final String OTHER_NODE_ID = "node-2";
    private static final VmNodeState ONLINE = new VmNodeState(VmNodeStatusType.ONLINE);
    private static final VmNodeState DRAINING = new VmNodeState(VmNodeStatusType.DRAINING);
    private static final long DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 90;

    @Autowired
    private WorkloadOrchestrationService orchestration;

    @Autowired
    private VmNodeOrchestrationService nodeOrchestration;

    @Autowired
    private Reconciler<VmNode> nodeWorker;

    @Autowired
    private WorkloadRepository workloads;

    @Autowired
    private VmNodeRepository nodes;

    @Autowired
    private ServiceRegistry serviceRegistry;

    @Autowired
    private KinoticSystemApiProperties properties;

    private final Map<String, StubVmManager> vmManagers = new HashMap<>();
    private StubVmManager vmManager;

    @BeforeEach
    public void setUp() throws Exception {
        registered(NODE_ID, 4, 4096, 10240);
        vmManager = vmManager(NODE_ID);
    }

    @AfterEach
    public void tearDown() throws Exception {
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(DEFAULT_HEARTBEAT_TIMEOUT_SECONDS);
        for (String nodeId : List.copyOf(vmManagers.keySet())) {
            unregisterVmManager(nodeId);
        }
        for (String nodeId : List.of(NODE_ID, OTHER_NODE_ID)) {
            for (Workload workload : await(workloads.findAllForNode(nodeId, Pageable.create(0, 500, null))).getContent()) {
                await(workloads.deleteById(workload.getId()));
            }
            if (await(nodes.findById(nodeId)) != null) {
                await(nodes.deleteByIdSync(nodeId));
            }
        }
        await(workloads.syncIndex());
    }

    @Test
    public void unreachableVmManagerMarksItsNodeUnreachableAtOnce() throws Exception {
        unregisterVmManager(NODE_ID);

        Exception failure = assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(newWorkload())));

        assertInstanceOf(RpcMissingServiceException.class, failure.getCause());
        assertTrue(awaitNodeUnreachable(true));
        assertFalse(node().getState().isReconciled(), "nothing is placed on it");
    }

    @Test
    public void markingUnreachableLeavesLastSeenAlone() throws Exception {
        unregisterVmManager(NODE_ID);
        call(() -> nodeOrchestration.heartbeat(NODE_ID, List.of()));
        Date heartbeatAt = node().getLastSeen();

        assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(newWorkload())));

        assertTrue(awaitNodeUnreachable(true));
        assertEquals(heartbeatAt, node().getLastSeen());
    }

    @Test
    public void registeringANewNodeStampsLastSeenAndMakesItPlaceable() throws Exception {
        Date before = new Date();

        VmNode registered = registered(OTHER_NODE_ID, 4, 4096, 10240);

        assertFalse(await(nodes.findById(OTHER_NODE_ID)).getLastSeen().before(before));
        assertEquals(ONLINE, registered.getState().getDesired());
        assertEquals(ONLINE, registered.getState().getObserved());
        assertTrue(registered.getState().isReconciled());
    }

    @Test
    public void registrationEndsTheNodesSilence() throws Exception {
        markedUnreachableBySilence();

        registered(NODE_ID, 4, 4096, 10240);

        assertFalse(nodeUnreachable());
        assertTrue(node().getState().isReconciled());
    }

    @Test
    public void reRegisteringANodeStampsLastSeen() throws Exception {
        silentFor(Duration.ofSeconds(10));
        Date before = new Date();

        registered(NODE_ID, 4, 4096, 10240);

        assertFalse(node().getLastSeen().before(before));
    }

    @Test
    public void ordinaryStartFailureDoesNotVerifyTheNode() throws Exception {
        vmManager.failStartWith = new RuntimeException("node exploded");

        assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(newWorkload())));

        // a mark from a verification would land on the context after the call returned
        Thread.sleep(1000);
        assertFalse(nodeUnreachable());
        assertTrue(node().getState().isReconciled());
    }

    @Test
    public void heartbeatBringsAnUnreachableNodeBack() throws Exception {
        unregisterVmManager(NODE_ID);
        assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(newWorkload())));
        assertTrue(awaitNodeUnreachable(true));

        call(() -> nodeOrchestration.heartbeat(NODE_ID, List.of()));

        assertFalse(nodeUnreachable());
        assertTrue(node().getState().isReconciled());
    }

    @Test
    public void heartbeatWithProblemsDrainsTheNodeUntilItReportsNone() throws Exception {
        VmNode draining = call(() -> nodeOrchestration.heartbeat(NODE_ID, List.of("disk limits unenforced", "metadata endpoint open")));

        assertEquals(DRAINING, draining.getState().getObserved());
        assertEquals("disk limits unenforced; metadata endpoint open", draining.getHealthMessage());
        assertFalse(draining.getState().isReconciled(), "nothing more is placed on it");

        VmNode fit = call(() -> nodeOrchestration.heartbeat(NODE_ID, List.of()));

        assertEquals(ONLINE, fit.getState().getObserved());
        assertNull(fit.getHealthMessage());
        assertTrue(fit.getState().isReconciled());
    }

    @Test
    public void silentNodeIsMarkedUnreachableWhateverItReportedLast() throws Exception {
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(1);
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));
        call(() -> nodeOrchestration.heartbeat(NODE_ID, List.of("telemetry shipping lost")));
        silentFor(Duration.ofSeconds(10));

        Requeue requeue = await(nodeWorker.reconcile(node()));

        assertTrue(nodeUnreachable());
        assertEquals(DRAINING, node().getState().getObserved(), "the node's last word stands beside the mark");
        // the run may still be live on the far side of the partition: the record keeps its status and its room
        assertTrue(unreachable(deployed.getId()));
        assertEquals(WorkloadStatus.RUNNING, workload(deployed.getId()).getStatus());
        assertEquals(3, node().getFreeCpus());
        assertEquals(Duration.ofSeconds(1), requeue.after(), "looked at again after another timeout");
    }

    @Test
    public void silentNodeAlreadyMarkedKeepsItsMark() throws Exception {
        Workload deployed = markedUnreachableBySilence();
        Date since = nodeUnreachableSince();
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(1);

        Requeue requeue = await(nodeWorker.reconcile(node()));

        assertEquals(since, nodeUnreachableSince(), "the first inference is what matters");
        assertEquals(1, node().getState().getConditions().size());
        assertTrue(unreachable(deployed.getId()));
        assertEquals(Duration.ofSeconds(1), requeue.after());
    }

    @Test
    public void nodeHeardWithinTheTimeoutIsLookedAtAgainWhenItsHeartbeatWouldBeOverdue() throws Exception {
        call(() -> nodeOrchestration.heartbeat(NODE_ID, List.of()));

        Requeue requeue = await(nodeWorker.reconcile(node()));

        assertFalse(nodeUnreachable());
        assertTrue(requeue.after().toMillis() > 80_000 && requeue.after().toMillis() <= 90_000, requeue.toString());
    }

    @Test
    public void silentNodeMarksItsStoppingWorkloadUnreachable() throws Exception {
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(1);
        // a stop whose reply never came back from the node leaves the record STOPPING
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));
        await(workloads.updateRunSync(deployed.getId(), WorkloadStatus.STOPPING, null, "unanswered stop"));
        silentFor(Duration.ofSeconds(10));

        await(nodeWorker.reconcile(node()));

        assertTrue(nodeUnreachable());
        assertTrue(unreachable(deployed.getId()));
        assertEquals(WorkloadStatus.STOPPING, workload(deployed.getId()).getStatus());
    }

    @Test
    public void reportFromANodeHeardAgainOutranksTheSilence() throws Exception {
        Workload deployed = markedUnreachableBySilence();

        // the node's clock says the run started long before the server inferred the silence
        reportAt(deployed.getId(), WorkloadStatus.RUNNING, null, System.currentTimeMillis() - 60_000);

        Workload stored = workload(deployed.getId());
        assertFalse(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
        assertEquals(WorkloadStatus.RUNNING, stored.getStatus());
        assertEquals(3, node().getFreeCpus(), "the room was never released");
    }

    @Test
    public void reportOfAnEndedRunFromANodeHeardAgainReleasesItsRoom() throws Exception {
        Workload deployed = markedUnreachableBySilence();

        reportAt(deployed.getId(), WorkloadStatus.STOPPED, 0, System.currentTimeMillis() - 60_000);

        Workload stored = workload(deployed.getId());
        assertFalse(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
        assertEquals(WorkloadStatus.STOPPED, stored.getStatus());
        assertEquals(0, stored.getExitCode());
        assertEquals(4, node().getFreeCpus());
    }

    @Test
    public void unansweredStartLeavesTheWorkloadStartingAndUnreachable() throws Exception {
        // a node that dies mid-call leaves the cluster, and the platform fails the call it was serving
        // with RpcServiceUnavailableException; the stub answers the way that call ends
        vmManager.failStartWith = new RpcServiceUnavailableException("node left the cluster mid-call");

        Exception failure = assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(newWorkload())));

        assertInstanceOf(RpcServiceUnavailableException.class, failure.getCause());
        Workload stored = await(workloads.findAllForNode(NODE_ID, Pageable.create(0, 10, null))).getContent().getFirst();
        assertEquals(WorkloadStatus.STARTING, stored.getStatus());
        assertTrue(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
        assertEquals(3, node().getFreeCpus(), "the node may be running it");

        // the node's report settles it
        report(stored.getId(), WorkloadStatus.RUNNING, null);
        Workload settled = workload(stored.getId());
        assertEquals(WorkloadStatus.RUNNING, settled.getStatus());
        assertFalse(StatusConditions.has(settled.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
    }

    @Test
    public void unansweredStopLeavesTheWorkloadStoppingAndUnreachable() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));
        vmManager.failStopWith = new RpcServiceUnavailableException("node left the cluster mid-call");

        Exception failure = assertThrows(Exception.class, () -> call(() -> orchestration.stopWorkload(deployed.getId())));

        assertInstanceOf(RpcServiceUnavailableException.class, failure.getCause());
        Workload stored = workload(deployed.getId());
        assertEquals(WorkloadStatus.STOPPING, stored.getStatus());
        assertTrue(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
        assertEquals(3, node().getFreeCpus());
    }

    @Test
    public void staleReportOfAnEarlierStateIsIgnored() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));
        call(() -> orchestration.stopWorkload(deployed.getId()));
        assertEquals(WorkloadStatus.STOPPED, workload(deployed.getId()).getStatus());

        // a snapshot the node took before it processed the stop, whatever its clock says
        reportAt(deployed.getId(), WorkloadStatus.RUNNING, null, System.currentTimeMillis() + 60_000);

        assertEquals(WorkloadStatus.STOPPED, workload(deployed.getId()).getStatus());
        assertEquals(4, node().getFreeCpus());
    }

    @Test
    public void runUpdateWrittenFromAReadTheEndOvertookIsDeclined() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));
        report(deployed.getId(), WorkloadStatus.STOPPED, 0);
        assertEquals(4, node().getFreeCpus());

        // the writes a stop and a node's report make from a read that showed the run open, landing
        // after the end: each is declined in the shard operation that would have applied it
        assertFalse(await(workloads.updateRunSync(deployed.getId(), WorkloadStatus.STOPPING, null, "stopWorkload")));
        assertFalse(await(workloads.updateRunSync(deployed.getId(), WorkloadStatus.RUNNING, null, "node " + NODE_ID)));

        Workload stored = workload(deployed.getId());
        assertEquals(WorkloadStatus.STOPPED, stored.getStatus());
        assertEquals(0, stored.getExitCode());
        // the node's own end of it, or a stop, returns nothing again
        report(deployed.getId(), WorkloadStatus.STOPPED, 0);
        call(() -> orchestration.stopWorkload(deployed.getId()));
        assertEquals(4, node().getFreeCpus());
    }

    @Test
    public void deregisteringAnUnreachableNodeRecordsItsOpenRunsFailed() throws Exception {
        Workload deployed = markedUnreachableBySilence();

        // the removal is intent; the node's worker, called by the master, carries it out
        call(() -> nodeOrchestration.deregisterNode(NODE_ID));

        awaitNodeGone(NODE_ID);
        assertEquals(WorkloadStatus.FAILED, workload(deployed.getId()).getStatus());
    }

    @Test
    public void aReportOfARunAlreadyEndedReturnsNoRoom() throws Exception {
        Workload deployed = markedUnreachableBySilence();
        call(() -> nodeOrchestration.deregisterNode(NODE_ID));
        awaitNodeGone(NODE_ID);
        assertEquals(WorkloadStatus.FAILED, workload(deployed.getId()).getStatus());

        // the node comes back, takes another run, then reports the end it saw of the first
        registered(NODE_ID, 4, 4096, 10240);
        call(() -> orchestration.deployWorkload(newWorkload()));
        assertEquals(3, node().getFreeCpus());

        report(deployed.getId(), WorkloadStatus.STOPPED, 137);

        assertEquals(WorkloadStatus.FAILED, workload(deployed.getId()).getStatus(), "the deregistration settled the run");
        assertEquals(3, node().getFreeCpus(), "room the deregistration took is not returned again");
    }

    @Test
    public void deregisteringAReachableIdleNodeIsFinalizedByItsWorker() throws Exception {
        call(() -> nodeOrchestration.deregisterNode(NODE_ID));

        awaitNodeGone(NODE_ID);
    }

    @Test
    public void deregisteringAReachableNodeWithRunningWorkloadsIsRefused() throws Exception {
        call(() -> orchestration.deployWorkload(newWorkload()));

        assertThrows(Exception.class, () -> call(() -> nodeOrchestration.deregisterNode(NODE_ID)));

        assertNull(node().getState().getDeletionRequested());
        assertTrue(node().getState().isReconciled());
    }

    @Test
    public void deregisteringAnUnknownNodeFails() {
        assertThrows(Exception.class, () -> call(() -> nodeOrchestration.deregisterNode("ghost")));
    }

    @Test
    public void detachedDeployCompletesOnceStarted() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));

        assertEquals(WorkloadStatus.RUNNING, deployed.getStatus());
        assertEquals(NODE_ID, deployed.getNodeId());
    }

    @Test
    public void foregroundDeployCompletesAtRunEnd() throws Exception {
        Future<Workload> run = runAsOrganization(() -> orchestration.deployWorkload(newForegroundWorkload()));
        assertTrue(vmManager.reached.await(30, TimeUnit.SECONDS), "the start never reached the node");
        assertFalse(run.isComplete());

        // Mid-run the node pushes its RUNNING transition while the reply stays pending
        report(vmManager.lastStarted.getId(), WorkloadStatus.RUNNING, null);
        assertFalse(run.isComplete());
        assertEquals(WorkloadStatus.RUNNING, workload(vmManager.lastStarted.getId()).getStatus());

        vmManager.completeRun(WorkloadStatus.STOPPED, 0);

        Workload finished = await(run);
        assertEquals(WorkloadStatus.STOPPED, finished.getStatus());
        assertEquals(0, finished.getExitCode());
        // the record keeps the outcome; the room is released, and the node removes the VM on its own
        assertEquals(WorkloadStatus.STOPPED, workload(finished.getId()).getStatus());
        assertEquals(0, workload(finished.getId()).getExitCode());
        assertTrue(vmManager.destroyed.isEmpty());
        assertEquals(4, node().getFreeCpus());
        assertEquals(10240, node().getFreeDiskMb());
    }

    @Test
    public void foregroundDeployCompletesWithFailureExitCode() throws Exception {
        Future<Workload> run = runAsOrganization(() -> orchestration.deployWorkload(newForegroundWorkload()));
        assertTrue(vmManager.reached.await(30, TimeUnit.SECONDS), "the start never reached the node");

        vmManager.completeRun(WorkloadStatus.FAILED, 137);

        Workload finished = await(run);
        assertEquals(WorkloadStatus.FAILED, finished.getStatus());
        assertEquals(137, finished.getExitCode());
    }

    @Test
    public void detachedReplyDoesNotClobberTerminalReport() throws Exception {
        // A short-lived detached workload: the node's terminal report is applied before the
        // start reply is processed, so the RUNNING reply must not overwrite it
        vmManager.onStart = started -> nodeOrchestration.reportWorkloadStatus(NODE_ID, List.of(statusReport(started.getId(), WorkloadStatus.STOPPED, 0,
                                                                                                                System.currentTimeMillis() + 1000)));

        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));

        assertEquals(WorkloadStatus.STOPPED, deployed.getStatus());
        assertEquals(0, deployed.getExitCode());
        assertEquals(WorkloadStatus.STOPPED, workload(deployed.getId()).getStatus());
    }

    @Test
    public void detachedExitCodeArrivesViaStatusReport() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));

        report(deployed.getId(), WorkloadStatus.STOPPED, 0);

        Workload stored = workload(deployed.getId());
        assertEquals(WorkloadStatus.STOPPED, stored.getStatus());
        assertEquals(0, stored.getExitCode());
    }

    @Test
    public void workloadThatFailsToStartReleasesItsRoom() throws Exception {
        vmManager.failStartWith = new RuntimeException("image not found");

        Exception failure = assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(newForegroundWorkload())));

        assertTrue(failure.getCause().getMessage().contains("image not found"), failure.getCause().getMessage());
        Workload stored = await(workloads.findAllForNode(NODE_ID, Pageable.create(0, 10, null))).getContent().getFirst();
        assertEquals(WorkloadStatus.FAILED, stored.getStatus());
        assertEquals(4, node().getFreeCpus());
        assertEquals(10240, node().getFreeDiskMb());
    }

    @Test
    public void foregroundRunThatFailsReleasesItsRoom() throws Exception {
        Future<Workload> run = runAsOrganization(() -> orchestration.deployWorkload(newForegroundWorkload()));
        assertTrue(vmManager.reached.await(30, TimeUnit.SECONDS), "the start never reached the node");

        vmManager.completeRun(WorkloadStatus.FAILED, 137);

        Workload finished = await(run);
        assertEquals(WorkloadStatus.FAILED, finished.getStatus());
        assertEquals(137, workload(finished.getId()).getExitCode());
        assertEquals(4, node().getFreeCpus());
        assertEquals(10240, node().getFreeDiskMb());
    }

    @Test
    public void pinnedDeployLandsOnRequestedNode() throws Exception {
        registered(OTHER_NODE_ID, 4, 4096, 10240);
        StubVmManager other = vmManager(OTHER_NODE_ID);

        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload().setNodeId(OTHER_NODE_ID)));

        VmNode target = await(nodes.findById(OTHER_NODE_ID));
        assertEquals(OTHER_NODE_ID, deployed.getNodeId());
        assertEquals(OTHER_NODE_ID, other.lastStarted.getNodeId());
        assertNull(vmManager.lastStarted);
        assertEquals(4 - deployed.getCpus(), target.getFreeCpus());
        assertEquals(4096 - deployed.getMemoryMb(), target.getFreeMemoryMb());
        assertEquals(10240 - deployed.getDiskSizeMb(), target.getFreeDiskMb());
    }

    @Test
    public void pinnedDeployFailsWhenNodeUnknown() throws Exception {
        assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(newWorkload().setNodeId("ghost"))));

        assertNull(vmManager.lastStarted);
        assertTrue(await(workloads.findAllForNode("ghost", Pageable.create(0, 10, null))).getContent().isEmpty());
    }

    @Test
    public void pinnedDeployFailsWhenNodeNotTakingWorkloads() throws Exception {
        VmNode other = registered(OTHER_NODE_ID, 4, 4096, 10240);
        await(nodes.reportObserved(OTHER_NODE_ID, DRAINING, other.getState().getGeneration(), "test"));

        assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(newWorkload().setNodeId(OTHER_NODE_ID))));

        assertNull(vmManager.lastStarted);
    }

    @Test
    public void concurrentDeploysCannotOverAllocateANode() throws Exception {
        // room for exactly one of the two workloads; the placement returns the same node to both
        registered(NODE_ID, 1, 4096, 10240);

        Future<Workload> first = runAsOrganization(() -> orchestration.deployWorkload(newWorkload().setCpus(1)));
        Future<Workload> second = runAsOrganization(() -> orchestration.deployWorkload(newWorkload().setCpus(1)));
        Future.join(first, second).toCompletionStage().toCompletableFuture().handle((v, t) -> null).get(30, TimeUnit.SECONDS);

        assertTrue(first.succeeded() != second.succeeded(), "exactly one deploy may hold the node's last vCPU");
        assertEquals(0, node().getFreeCpus());
        assertEquals(1, vmManager.started.size());
    }

    @Test
    public void reservationDeclinesANodeThatLeftItsDesiredStateSinceItWasPicked() throws Exception {
        // placement read the node taking workloads; it reported a problem before the reservation was written
        call(() -> nodeOrchestration.heartbeat(NODE_ID, List.of("disk limits unenforced")));

        assertFalse(await(nodes.reserveSync(NODE_ID, newWorkload())));
        assertEquals(4, node().getFreeCpus(), "a node not in its desired state grants no room");

        call(() -> nodeOrchestration.heartbeat(NODE_ID, List.of()));

        assertTrue(await(nodes.reserveSync(NODE_ID, newWorkload())));
        assertEquals(3, node().getFreeCpus());
    }

    @Test
    public void destroyReturnsTheWorkloadsRoom() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));
        assertEquals(4 - deployed.getCpus(), node().getFreeCpus());

        call(() -> orchestration.destroyWorkload(deployed.getId()));

        assertEquals(4, node().getFreeCpus());
        assertEquals(4096, node().getFreeMemoryMb());
        assertEquals(10240, node().getFreeDiskMb());
        // the destroy ended the run; the record is its outcome
        assertEquals(List.of(deployed.getId()), vmManager.destroyed);
        assertEquals(WorkloadStatus.STOPPED, workload(deployed.getId()).getStatus());
    }

    @Test
    public void deleteRefusesAnOpenRun() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));

        assertThrows(Exception.class, () -> call(() -> orchestration.deleteWorkload(deployed.getId())));

        assertNotNull(workload(deployed.getId()));
    }

    @Test
    public void deletingABatchWithAnOpenRunDeletesNothing() throws Exception {
        Workload ended = call(() -> orchestration.deployWorkload(newWorkload()));
        call(() -> orchestration.stopWorkload(ended.getId()));
        Workload running = call(() -> orchestration.deployWorkload(newWorkload()));

        assertThrows(Exception.class, () -> call(() -> orchestration.deleteWorkloads(List.of(ended.getId(), running.getId()))));

        assertNotNull(workload(ended.getId()));
        assertNotNull(workload(running.getId()));
    }

    @Test
    public void stoppedRunReturnsItsRoomOnce() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload().setCpus(0.5)));
        assertEquals(3.5, node().getFreeCpus());

        call(() -> orchestration.stopWorkload(deployed.getId()));

        VmNode node = node();
        assertEquals(4, node.getFreeCpus());
        assertEquals(4096, node.getFreeMemoryMb());
        assertEquals(10240, node.getFreeDiskMb());
        assertEquals(WorkloadStatus.STOPPED, workload(deployed.getId()).getStatus());

        // the node's own report of the same end, and a later destroy, return nothing twice; the
        // exit code the stop did not have is still adopted
        report(deployed.getId(), WorkloadStatus.STOPPED, 0);
        assertEquals(4, node().getFreeCpus());
        assertEquals(0, workload(deployed.getId()).getExitCode());
        call(() -> orchestration.destroyWorkload(deployed.getId()));
        assertEquals(4, node().getFreeCpus());
        assertEquals(WorkloadStatus.STOPPED, workload(deployed.getId()).getStatus(), "the record outlives the run");
    }

    @Test
    public void statusReportOfAnEndedRunReturnsItsRoom() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload().setCpus(2)));
        assertEquals(2, node().getFreeCpus());

        report(deployed.getId(), WorkloadStatus.FAILED, 137);

        assertEquals(4, node().getFreeCpus());
        assertEquals(4096, node().getFreeMemoryMb());
        assertEquals(10240, node().getFreeDiskMb());
        assertEquals(137, workload(deployed.getId()).getExitCode());
    }

    @Test
    public void registrationRebuildsFreeCapacityFromTheWorkloadRecords() throws Exception {
        Workload running = call(() -> orchestration.deployWorkload(newWorkload().setCpus(1)));
        Workload ended = call(() -> orchestration.deployWorkload(newWorkload().setCpus(1)));
        call(() -> orchestration.stopWorkload(ended.getId()));
        // the node comes back with more CPU, and a leak the counters do not know about
        VmNode leaked = node();
        leaked.setFreeCpus(0);
        await(nodes.saveSync(leaked));

        VmNode registered = registered(NODE_ID, 8, 4096, 10240);

        assertEquals(8 - running.getCpus(), registered.getFreeCpus());
        assertEquals(4096 - running.getMemoryMb(), registered.getFreeMemoryMb());
        assertEquals(10240 - running.getDiskSizeMb(), registered.getFreeDiskMb(), "an ended run holds nothing");
    }

    @Test
    public void pinnedDeployFailsWhenNodeLacksCapacity() throws Exception {
        registered(OTHER_NODE_ID, 1, 4096, 10240);
        StubVmManager other = vmManager(OTHER_NODE_ID);

        assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(newWorkload().setNodeId(OTHER_NODE_ID).setCpus(2))));

        assertNull(other.lastStarted);
    }

    @Test
    public void secretValuesRedactedInRecordButRealOnNode() throws Exception {
        Workload deployed = call(() -> orchestration.deployWorkload(
                newWorkload().setEnvironment(new LinkedHashMap<>(Map.of("LOG_LEVEL", "debug")))
                             .setSecrets(new LinkedHashMap<>(Map.of("GIT_TOKEN", "secret")))));

        // The node received the real secret; the record only ever held the mask, including
        // when the node's start reply (which echoes environment and secrets) was persisted.
        // Plain environment entries persist verbatim.
        assertEquals("secret", vmManager.lastStarted.getSecrets().get("GIT_TOKEN"));
        assertEquals("<redacted>", workload(deployed.getId()).getSecrets().get("GIT_TOKEN"));
        assertEquals("debug", workload(deployed.getId()).getEnvironment().get("LOG_LEVEL"));
        assertEquals("secret", deployed.getSecrets().get("GIT_TOKEN"));
    }

    @Test
    public void secretValuesRedactedWhenStartFails() throws Exception {
        vmManager.failStartWith = new RuntimeException("node exploded");

        assertThrows(Exception.class, () -> call(() -> orchestration.deployWorkload(
                newWorkload().setSecrets(new LinkedHashMap<>(Map.of("GIT_TOKEN", "secret"))))));

        Workload stored = await(workloads.findAllForNode(NODE_ID, Pageable.create(0, 10, null))).getContent().getFirst();
        assertEquals(WorkloadStatus.FAILED, stored.getStatus());
        assertEquals("<redacted>", stored.getSecrets().get("GIT_TOKEN"));
    }

    /** Registers the node the way a vm-manager does at startup, taking workloads and heard just now. */
    private VmNode registered(String nodeId, int cpus, int memoryMb, int diskMb) throws Exception {
        VmNodeRegistration registration = new VmNodeRegistration().setId(nodeId)
                                                                  .setName(nodeId)
                                                                  .setHostname("host-" + nodeId)
                                                                  .setTotalCpus(cpus)
                                                                  .setTotalMemoryMb(memoryMb)
                                                                  .setTotalDiskMb(diskMb)
                                                                  .setWorkloadDataDir("/var/lib/kinotic/" + nodeId);
        return call(() -> nodeOrchestration.registerNode(registration));
    }

    /** Serves a stand-in vm-manager at the node's address, the one the platform's proxy sends to. */
    private StubVmManager vmManager(String nodeId) throws Exception {
        StubVmManager stub = new StubVmManager();
        await(serviceRegistry.register(vmManagerAddress(nodeId), VmManager.class, stub));
        vmManagers.put(nodeId, stub);
        return stub;
    }

    private void unregisterVmManager(String nodeId) throws Exception {
        if (vmManagers.remove(nodeId) != null) {
            await(serviceRegistry.unregister(vmManagerAddress(nodeId)));
        }
    }

    private static ServiceIdentifier vmManagerAddress(String nodeId) {
        ServiceIdentifier vmManager = KinoticUtil.serviceIdentifierOf(VmManagerProxy.class);
        return new ServiceIdentifier(vmManager.zone(), vmManager.namespace(), vmManager.name(), nodeId, vmManager.version());
    }

    /** The proxy sends as the participant on the calling context, so every orchestration call is made as one. */
    private <T> T call(Supplier<Future<T>> operation) throws Exception {
        return await(runAsOrganization(operation));
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private VmNode node() throws Exception {
        return await(nodes.findById(NODE_ID));
    }

    private Workload workload(String workloadId) throws Exception {
        return await(workloads.findById(workloadId));
    }

    /** Backdates the node's last heartbeat, as a node that fell silent that long ago stands. */
    private void silentFor(Duration silence) throws Exception {
        VmNode node = node();
        node.setLastSeen(new Date(System.currentTimeMillis() - silence.toMillis()));
        await(nodes.saveSync(node));
    }

    /**
     * Returns whether {@link #NODE_ID} carries NODE_UNREACHABLE once that is {@code expected}, or whatever
     * it is when the wait runs out. The mark made by verifyNode's continuation lands on the Vertx context
     * after the call the test awaited returned.
     */
    private boolean awaitNodeUnreachable(boolean expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (nodeUnreachable() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        return nodeUnreachable();
    }

    /** Waits for the master to call the node's worker for its deletion and the worker to delete the record. */
    private void awaitNodeGone(String nodeId) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (await(nodes.findById(nodeId)) != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
        }
        assertNull(await(nodes.findById(nodeId)), "the node's worker should have deleted " + nodeId);
    }

    private boolean nodeUnreachable() throws Exception {
        return StatusConditions.has(node().getState().getConditions(), StatusConditionType.NODE_UNREACHABLE);
    }

    private Date nodeUnreachableSince() throws Exception {
        return StatusConditions.find(node().getState().getConditions(), StatusConditionType.NODE_UNREACHABLE)
                               .map(StatusCondition::since)
                               .orElse(null);
    }

    private boolean unreachable(String workloadId) throws Exception {
        return StatusConditions.has(workload(workloadId).getState().getConditions(), StatusConditionType.NODE_UNREACHABLE);
    }

    /**
     * Deploys a workload and calls the node's worker with the heartbeat overdue, as the master does,
     * returning the workload once the worker has marked it unreachable. The default heartbeat standard
     * is back in force on return: the master keeps calling the worker for the node, and by the
     * one-second standard a node is silent again before its own registration has finished writing.
     */
    private Workload markedUnreachableBySilence() throws Exception {
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(1);
        Workload deployed = call(() -> orchestration.deployWorkload(newWorkload()));
        silentFor(Duration.ofSeconds(10));

        await(nodeWorker.reconcile(node()));
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(DEFAULT_HEARTBEAT_TIMEOUT_SECONDS);

        assertTrue(nodeUnreachable());
        assertTrue(unreachable(deployed.getId()));
        return deployed;
    }

    private static Workload newWorkload() {
        return new Workload("test-workload", "alpine:latest");
    }

    private static Workload newForegroundWorkload() {
        return newWorkload().setDetached(false);
    }

    /**
     * The node pushes a status report, stamped ahead of the record's last save so the report applies
     * even when both happen within the same millisecond.
     */
    private void report(String workloadId, WorkloadStatus status, Integer exitCode) throws Exception {
        reportAt(workloadId, status, exitCode, System.currentTimeMillis() + 1000);
    }

    /** The node pushes a status report stamped by its own clock. */
    private void reportAt(String workloadId, WorkloadStatus status, Integer exitCode, long updated) throws Exception {
        await(nodeOrchestration.reportWorkloadStatus(NODE_ID, List.of(statusReport(workloadId, status, exitCode, updated))));
    }

    private static WorkloadStatusReport statusReport(String workloadId, WorkloadStatus status, Integer exitCode, long updated) {
        return new WorkloadStatusReport().setWorkloadId(workloadId)
                                         .setStatus(status)
                                         .setExitCode(exitCode)
                                         .setUpdated(updated);
    }
}
