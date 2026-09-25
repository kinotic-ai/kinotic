package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.core.api.event.CRI;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Date;
import reactor.core.publisher.Flux;
import org.kinotic.core.api.exceptions.RpcServiceUnavailableException;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import org.kinotic.core.api.event.ListenerStatus;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.StatusConditions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.system.api.config.KinoticSystemApiProperties;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeState;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.system.api.workload.VmNodeRegistration;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.system.api.workload.WorkloadStatusReport;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior of the detached contract in workload orchestration: a detached deploy returns at
 * start while a non-detached one returns at run end, exercised through the real orchestration
 * services against in-memory stubs for persistence and the node's vm-manager. The node's worker
 * is called directly, as the reconcile master calls it.
 */
public class WorkloadOrchestrationTest {

    private static final String NODE_ID = "node-1";
    private static final VmNodeState ONLINE = new VmNodeState(VmNodeStatusType.ONLINE);
    private static final VmNodeState DRAINING = new VmNodeState(VmNodeStatusType.DRAINING);

    private StubWorkloadService workloads;
    private StubVmNodeService nodes;
    private StubVmManagerProxy vmManager;
    private StubLokiClient loki;
    private EventBusService eventBus;
    private Vertx vertx;
    private KinoticSystemApiProperties properties;
    private DefaultVmNodeOrchestrationService nodeOrchestration;
    private DefaultWorkloadOrchestrationService orchestration;

    @BeforeEach
    void setUp() {
        workloads = new StubWorkloadService();
        nodes = new StubVmNodeService();
        // a node with room for the default workload; a deploy reserves on the stored record
        nodes.availableNode = online(new VmNode(NODE_ID, "node-1", "host-1"));
        nodes.availableNode.setTotalCpus(4).setTotalMemoryMb(4096).setTotalDiskMb(10240)
                           .setFreeCpus(4).setFreeMemoryMb(4096).setFreeDiskMb(10240);
        nodes.saveSync(nodes.availableNode);
        vmManager = new StubVmManagerProxy();
        loki = new StubLokiClient();
        // the node's vm-manager registration, as the cluster reports it: absent unless a test says otherwise
        eventBus = mock(EventBusService.class);
        when(eventBus.monitorListenerStatus(any())).thenReturn(Flux.just(ListenerStatus.INACTIVE));
        properties = new KinoticSystemApiProperties();
        vertx = Vertx.vertx();
        nodeOrchestration = new DefaultVmNodeOrchestrationService(properties, nodes, workloads, eventBus, vertx);
        // the retention sweep is deployed on the Ignite service grid, which these tests do not run
        orchestration = new DefaultWorkloadOrchestrationService(nodeOrchestration, vmManager,
                                                                nodes, workloads, loki, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        await(vertx.close());
    }

    @Test
    public void unreachableVmManagerMarksItsNodeUnreachableAtOnce() throws Exception {
        vmManager.failStartWith = new RpcMissingServiceException("no vm-manager registered for node-1");

        Exception failure = assertThrows(Exception.class, () -> await(orchestration.deployWorkload(newWorkload())));
        assertInstanceOf(RpcMissingServiceException.class, failure.getCause());

        // the registration checked is the vm-manager scoped to this node, the address the proxy sends to
        ArgumentCaptor<CRI> watched = ArgumentCaptor.forClass(CRI.class);
        verify(eventBus).monitorListenerStatus(watched.capture());
        assertEquals(NODE_ID, watched.getValue().scope());
        assertTrue(watched.getValue().raw().contains("VmManager"), watched.getValue().raw());
        assertTrue(awaitNodeUnreachable(true));
        assertFalse(nodes.saved.get(NODE_ID).getState().isReconciled(), "nothing is placed on it");
    }

    @Test
    public void markingUnreachableLeavesLastSeenAlone() throws Exception {
        // The placement read (availableNode) predates this heartbeat, so a full save of it would
        // put the older lastSeen back
        Date heartbeatAt = await(nodeOrchestration.heartbeat(NODE_ID, List.of())).getLastSeen();
        vmManager.failStartWith = new RpcMissingServiceException("gone");

        assertThrows(Exception.class, () -> await(orchestration.deployWorkload(newWorkload())));

        assertTrue(awaitNodeUnreachable(true));
        assertEquals(heartbeatAt, nodes.saved.get(NODE_ID).getLastSeen());
    }

    @Test
    public void registeringANewNodeStampsLastSeenAndMakesItPlaceable() throws Exception {
        Date before = new Date();

        VmNode registered = await(nodeOrchestration.registerNode(new VmNodeRegistration("node-2", "node-2", "host-2")));

        assertFalse(nodes.saved.get("node-2").getLastSeen().before(before));
        assertEquals(ONLINE, registered.getState().getDesired());
        assertEquals(ONLINE, registered.getState().getObserved());
        assertTrue(registered.getState().isReconciled());
    }

    @Test
    public void registrationEndsTheNodesSilence() throws Exception {
        markedUnreachableBySilence();

        await(nodeOrchestration.registerNode(new VmNodeRegistration(NODE_ID, "node-1", "host-1")));

        assertFalse(nodeUnreachable());
        assertTrue(nodes.saved.get(NODE_ID).getState().isReconciled());
    }

    @Test
    public void reRegisteringANodeStampsLastSeen() throws Exception {
        VmNode node = nodes.saved.get(NODE_ID);
        node.setLastSeen(new Date(System.currentTimeMillis() - 10_000));
        nodes.saveSync(node);
        Date before = new Date();

        await(nodeOrchestration.registerNode(new VmNodeRegistration(NODE_ID, "node-1", "host-1")));

        assertFalse(nodes.saved.get(NODE_ID).getLastSeen().before(before));
    }

    @Test
    public void ordinaryStartFailureDoesNotVerifyTheNode() throws Exception {
        vmManager.failStartWith = new RuntimeException("node exploded");

        assertThrows(Exception.class, () -> await(orchestration.deployWorkload(newWorkload())));

        verify(eventBus, never()).monitorListenerStatus(any());
        assertFalse(nodeUnreachable());
    }

    @Test
    public void heartbeatBringsAnUnreachableNodeBack() throws Exception {
        vmManager.failStartWith = new RpcMissingServiceException("gone");
        assertThrows(Exception.class, () -> await(orchestration.deployWorkload(newWorkload())));
        assertTrue(awaitNodeUnreachable(true));

        await(nodeOrchestration.heartbeat(NODE_ID, List.of()));

        assertFalse(nodeUnreachable());
        assertTrue(nodes.saved.get(NODE_ID).getState().isReconciled());
    }

    @Test
    public void heartbeatWithProblemsDrainsTheNodeUntilItReportsNone() throws Exception {
        VmNode draining = await(nodeOrchestration.heartbeat(NODE_ID, List.of("disk limits unenforced", "metadata endpoint open")));

        assertEquals(DRAINING, draining.getState().getObserved());
        assertEquals("disk limits unenforced; metadata endpoint open", draining.getHealthMessage());
        assertFalse(draining.getState().isReconciled(), "nothing more is placed on it");

        VmNode fit = await(nodeOrchestration.heartbeat(NODE_ID, List.of()));

        assertEquals(ONLINE, fit.getState().getObserved());
        assertNull(fit.getHealthMessage());
        assertTrue(fit.getState().isReconciled());
    }

    @Test
    public void reachableVmManagerKeepsItsNodeReachableWhenACallFails() throws Exception {
        when(eventBus.monitorListenerStatus(any())).thenReturn(Flux.just(ListenerStatus.ACTIVE));
        vmManager.failStartWith = new RpcServiceUnavailableException("the gateway serving it left mid-call");

        assertThrows(Exception.class, () -> await(orchestration.deployWorkload(newWorkload())));

        assertFalse(nodeUnreachable());
    }

    @Test
    public void silentNodeIsMarkedUnreachableWhateverItReportedLast() throws Exception {
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(1);
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));
        await(nodeOrchestration.heartbeat(NODE_ID, List.of("telemetry shipping lost")));
        VmNode node = nodes.saved.get(NODE_ID);
        node.setLastSeen(new Date(System.currentTimeMillis() - 10_000));
        nodes.saveSync(node);

        Requeue requeue = await(nodeOrchestration.reconcile(await(nodes.findById(NODE_ID))));

        assertTrue(nodeUnreachable());
        assertEquals(DRAINING, nodes.saved.get(NODE_ID).getState().getObserved(), "the node's last word stands beside the mark");
        // the run may still be live on the far side of the partition: the record keeps its status and its room
        assertTrue(unreachable(deployed.getId()));
        assertEquals(WorkloadStatus.RUNNING, workloads.saved.get(deployed.getId()).getStatus());
        assertEquals(3, nodes.saved.get(NODE_ID).getFreeCpus());
        assertEquals(Duration.ofSeconds(1), requeue.after(), "looked at again after another timeout");
    }

    @Test
    public void silentNodeAlreadyMarkedKeepsItsMark() throws Exception {
        Workload deployed = markedUnreachableBySilence();
        Date since = nodeUnreachableSince();

        Requeue requeue = await(nodeOrchestration.reconcile(await(nodes.findById(NODE_ID))));

        assertEquals(since, nodeUnreachableSince(), "the first inference is what matters");
        assertEquals(1, nodes.saved.get(NODE_ID).getState().getConditions().size());
        assertTrue(unreachable(deployed.getId()));
        assertEquals(Duration.ofSeconds(1), requeue.after());
    }

    @Test
    public void nodeHeardWithinTheTimeoutIsLookedAtAgainWhenItsHeartbeatWouldBeOverdue() throws Exception {
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(90);
        await(nodeOrchestration.heartbeat(NODE_ID, List.of()));

        Requeue requeue = await(nodeOrchestration.reconcile(await(nodes.findById(NODE_ID))));

        assertFalse(nodeUnreachable());
        assertTrue(requeue.after().toMillis() > 80_000 && requeue.after().toMillis() <= 90_000, requeue.toString());
    }

    @Test
    public void silentNodeMarksItsStoppingWorkloadUnreachable() throws Exception {
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(1);
        // a stop whose reply never came back from the node leaves the record STOPPING
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));
        workloads.saved.get(deployed.getId()).setStatus(WorkloadStatus.STOPPING);
        VmNode node = nodes.saved.get(NODE_ID);
        node.setLastSeen(new Date(System.currentTimeMillis() - 10_000));
        nodes.saveSync(node);

        await(nodeOrchestration.reconcile(await(nodes.findById(NODE_ID))));

        assertTrue(nodeUnreachable());
        assertTrue(unreachable(deployed.getId()));
        assertEquals(WorkloadStatus.STOPPING, workloads.saved.get(deployed.getId()).getStatus());
    }

    @Test
    public void reportFromANodeHeardAgainOutranksTheSilence() throws Exception {
        Workload deployed = markedUnreachableBySilence();

        // the node's clock says the run started long before the server inferred the silence
        reportAt(deployed.getId(), WorkloadStatus.RUNNING, null, System.currentTimeMillis() - 60_000);

        Workload stored = workloads.saved.get(deployed.getId());
        assertFalse(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
        assertEquals(WorkloadStatus.RUNNING, stored.getStatus());
        assertEquals(3, nodes.saved.get(NODE_ID).getFreeCpus(), "the room was never released");
    }

    @Test
    public void reportOfAnEndedRunFromANodeHeardAgainReleasesItsRoom() throws Exception {
        Workload deployed = markedUnreachableBySilence();

        reportAt(deployed.getId(), WorkloadStatus.STOPPED, 0, System.currentTimeMillis() - 60_000);

        Workload stored = workloads.saved.get(deployed.getId());
        assertFalse(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
        assertEquals(WorkloadStatus.STOPPED, stored.getStatus());
        assertEquals(0, stored.getExitCode());
        assertEquals(4, nodes.saved.get(NODE_ID).getFreeCpus());
    }

    @Test
    public void unansweredStartLeavesTheWorkloadStartingAndUnreachable() throws Exception {
        when(eventBus.monitorListenerStatus(any())).thenReturn(Flux.just(ListenerStatus.ACTIVE));
        vmManager.failStartWith = new RpcServiceUnavailableException("the node left mid-call");

        assertThrows(Exception.class, () -> await(orchestration.deployWorkload(newWorkload())));

        Workload stored = workloads.saved.values().iterator().next();
        assertEquals(WorkloadStatus.STARTING, stored.getStatus());
        assertTrue(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
        assertEquals(3, nodes.saved.get(NODE_ID).getFreeCpus(), "the node may be running it");

        // the node's report settles it
        report(stored.getId(), WorkloadStatus.RUNNING, null);
        Workload settled = workloads.saved.get(stored.getId());
        assertEquals(WorkloadStatus.RUNNING, settled.getStatus());
        assertFalse(StatusConditions.has(settled.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
    }

    @Test
    public void unansweredStopLeavesTheWorkloadStoppingAndUnreachable() throws Exception {
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));
        when(eventBus.monitorListenerStatus(any())).thenReturn(Flux.just(ListenerStatus.ACTIVE));
        vmManager.failStopWith = new RpcServiceUnavailableException("the node left mid-call");

        assertThrows(Exception.class, () -> await(orchestration.stopWorkload(deployed.getId())));

        Workload stored = workloads.saved.get(deployed.getId());
        assertEquals(WorkloadStatus.STOPPING, stored.getStatus());
        assertTrue(StatusConditions.has(stored.getState().getConditions(), StatusConditionType.NODE_UNREACHABLE));
        assertEquals(3, nodes.saved.get(NODE_ID).getFreeCpus());
    }

    @Test
    public void staleReportOfAnEarlierStateIsIgnored() throws Exception {
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));
        await(orchestration.stopWorkload(deployed.getId()));
        assertEquals(WorkloadStatus.STOPPED, workloads.saved.get(deployed.getId()).getStatus());

        // a snapshot the node took before it processed the stop, whatever its clock says
        reportAt(deployed.getId(), WorkloadStatus.RUNNING, null, System.currentTimeMillis() + 60_000);

        assertEquals(WorkloadStatus.STOPPED, workloads.saved.get(deployed.getId()).getStatus());
        assertEquals(4, nodes.saved.get(NODE_ID).getFreeCpus());
    }

    @Test
    public void deregisteringAnUnreachableNodeRecordsItsOpenRunsFailed() throws Exception {
        Workload deployed = markedUnreachableBySilence();

        await(nodeOrchestration.deregisterNode(NODE_ID));

        // the removal is intent; the node's worker carries it out
        assertNotNull(nodes.saved.get(NODE_ID).getState().getDeletionRequested());
        assertEquals(WorkloadStatus.RUNNING, workloads.saved.get(deployed.getId()).getStatus());

        Requeue requeue = await(nodeOrchestration.reconcile(await(nodes.findById(NODE_ID))));

        assertEquals(WorkloadStatus.FAILED, workloads.saved.get(deployed.getId()).getStatus());
        assertNull(nodes.saved.get(NODE_ID));
        assertEquals(Requeue.NONE, requeue);
    }

    @Test
    public void deregisteringAReachableIdleNodeAsksForItsRemoval() throws Exception {
        await(nodeOrchestration.deregisterNode(NODE_ID));

        assertNotNull(nodes.saved.get(NODE_ID).getState().getDeletionRequested());
        assertFalse(nodes.saved.get(NODE_ID).getState().isReconciled(), "nothing is placed on it meanwhile");

        await(nodeOrchestration.reconcile(await(nodes.findById(NODE_ID))));

        assertNull(nodes.saved.get(NODE_ID));
    }

    @Test
    public void deregisteringAReachableNodeWithRunningWorkloadsIsRefused() throws Exception {
        await(orchestration.deployWorkload(newWorkload()));

        assertThrows(Exception.class, () -> await(nodeOrchestration.deregisterNode(NODE_ID)));

        assertNull(nodes.saved.get(NODE_ID).getState().getDeletionRequested());
        assertTrue(nodes.saved.get(NODE_ID).getState().isReconciled());
    }

    @Test
    public void deregisteringAnUnknownNodeFails() {
        assertThrows(Exception.class, () -> await(nodeOrchestration.deregisterNode("ghost")));
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
        // the record keeps the outcome; the room is released, and the node removes the VM on its own
        assertEquals(WorkloadStatus.STOPPED, workloads.saved.get(finished.getId()).getStatus());
        assertEquals(0, workloads.saved.get(finished.getId()).getExitCode());
        assertTrue(vmManager.destroyed.isEmpty());
        assertEquals(4, nodes.saved.get(NODE_ID).getFreeCpus());
        assertEquals(10240, nodes.saved.get(NODE_ID).getFreeDiskMb());
        assertTrue(nodes.saved.get(NODE_ID).getReservations().isEmpty());
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
    public void workloadThatFailsToStartReleasesItsRoom() {
        vmManager.failStartWith = new RuntimeException("image not found");

        Future<Workload> run = orchestration.deployWorkload(newForegroundWorkload());

        assertTrue(run.failed());
        assertEquals("image not found", run.cause().getMessage());
        assertEquals(WorkloadStatus.FAILED, workloads.saved.values().iterator().next().getStatus());
        assertEquals(4, nodes.saved.get(NODE_ID).getFreeCpus());
        assertEquals(10240, nodes.saved.get(NODE_ID).getFreeDiskMb());
    }

    @Test
    public void foregroundRunThatFailsReleasesItsRoom() throws Exception {
        Future<Workload> run = orchestration.deployWorkload(newForegroundWorkload());

        vmManager.completeRun(WorkloadStatus.FAILED, 137);

        Workload finished = await(run);
        assertEquals(WorkloadStatus.FAILED, finished.getStatus());
        assertEquals(137, workloads.saved.get(finished.getId()).getExitCode());
        assertEquals(4, nodes.saved.get(NODE_ID).getFreeCpus());
        assertEquals(10240, nodes.saved.get(NODE_ID).getFreeDiskMb());
    }

    @Test
    public void pinnedDeployLandsOnRequestedNode() throws Exception {
        VmNode target = registeredNode("node-2", 4, 4096, 10240);

        Workload deployed = await(orchestration.deployWorkload(newWorkload().setNodeId("node-2")));

        assertEquals("node-2", deployed.getNodeId());
        assertEquals("node-2", vmManager.lastStarted.getNodeId());
        assertEquals(4 - deployed.getCpus(), target.getFreeCpus());
        assertEquals(4096 - deployed.getMemoryMb(), target.getFreeMemoryMb());
        assertEquals(10240 - deployed.getDiskSizeMb(), target.getFreeDiskMb());
    }

    @Test
    public void pinnedDeployFailsWhenNodeUnknown() {
        Future<Workload> run = orchestration.deployWorkload(newWorkload().setNodeId("ghost"));

        assertTrue(run.failed());
        assertNull(vmManager.lastStarted);
        assertTrue(workloads.saved.isEmpty());
    }

    @Test
    public void pinnedDeployFailsWhenNodeNotTakingWorkloads() {
        registeredNode("node-2", 4, 4096, 10240)
                .getState().setObserved(DRAINING).setReconciled(false);

        Future<Workload> run = orchestration.deployWorkload(newWorkload().setNodeId("node-2"));

        assertTrue(run.failed());
        assertNull(vmManager.lastStarted);
    }

    @Test
    public void concurrentDeploysCannotOverAllocateANode() throws Exception {
        // room for exactly one of the two workloads; the placement returns the same node to both
        nodes.availableNode = registeredNode(NODE_ID, 1, 4096, 10240);

        Future<Workload> first = orchestration.deployWorkload(newWorkload().setCpus(1));
        Future<Workload> second = orchestration.deployWorkload(newWorkload().setCpus(1));
        Future.join(first, second).toCompletionStage().toCompletableFuture().handle((v, t) -> null).get(5, TimeUnit.SECONDS);

        assertTrue(first.succeeded() != second.succeeded(), "exactly one deploy may hold the node's last vCPU");
        assertEquals(0, nodes.saved.get(NODE_ID).getFreeCpus());
        assertEquals(1, vmManager.started.size());
    }

    @Test
    public void destroyReturnsTheWorkloadsReservation() throws Exception {
        nodes.availableNode = registeredNode(NODE_ID, 4, 4096, 10240);

        Workload deployed = await(orchestration.deployWorkload(newWorkload()));
        assertEquals(4 - deployed.getCpus(), nodes.saved.get(NODE_ID).getFreeCpus());

        await(orchestration.destroyWorkload(deployed.getId()));
        assertEquals(4, nodes.saved.get(NODE_ID).getFreeCpus());
        assertEquals(4096, nodes.saved.get(NODE_ID).getFreeMemoryMb());
        assertEquals(10240, nodes.saved.get(NODE_ID).getFreeDiskMb());
        assertTrue(nodes.saved.get(NODE_ID).getReservations().isEmpty());
        // the destroy ended the run; the record is its outcome
        assertEquals(List.of(deployed.getId()), vmManager.destroyed);
        assertEquals(WorkloadStatus.STOPPED, workloads.saved.get(deployed.getId()).getStatus());
    }

    @Test
    public void deleteRefusesAnOpenRun() throws Exception {
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));

        assertThrows(Exception.class, () -> await(orchestration.deleteWorkload(deployed.getId())));

        assertNotNull(workloads.saved.get(deployed.getId()));
        assertNull(loki.deletedQuery);
    }

    @Test
    public void deleteRemovesTheRecordWithItsLogs() throws Exception {
        Workload deployed = await(orchestration.deployWorkload(newWorkload().setOrganizationId("acme")));
        await(orchestration.stopWorkload(deployed.getId()));

        await(orchestration.deleteWorkload(deployed.getId()));

        assertNull(workloads.saved.get(deployed.getId()));
        assertEquals("acme", loki.deletedTenant);
        assertEquals("{workload_id=\"" + deployed.getId() + "\"}", loki.deletedQuery);
    }

    @Test
    public void deletingABatchAsksTheLogStoreOncePerTenant() throws Exception {
        Workload first = await(orchestration.deployWorkload(newWorkload().setOrganizationId("acme")));
        Workload second = await(orchestration.deployWorkload(newWorkload().setOrganizationId("acme")));
        Workload other = await(orchestration.deployWorkload(newWorkload().setOrganizationId("globex")));
        for (Workload workload : List.of(first, second, other)) {
            await(orchestration.stopWorkload(workload.getId()));
        }

        await(orchestration.deleteWorkloads(List.of(first.getId(), second.getId(), other.getId())));

        assertEquals(List.of("acme {workload_id=~\"" + first.getId() + "|" + second.getId() + "\"}",
                             "globex {workload_id=\"" + other.getId() + "\"}"),
                     loki.deletes);
        assertTrue(workloads.saved.isEmpty());
    }

    @Test
    public void deletingABatchWithAnOpenRunDeletesNothing() throws Exception {
        Workload ended = await(orchestration.deployWorkload(newWorkload()));
        await(orchestration.stopWorkload(ended.getId()));
        Workload running = await(orchestration.deployWorkload(newWorkload()));

        assertThrows(Exception.class, () -> await(orchestration.deleteWorkloads(List.of(ended.getId(), running.getId()))));

        assertEquals(2, workloads.saved.size());
        assertTrue(loki.deletes.isEmpty());
    }

    @Test
    public void platformWorkloadLogsLiveInTheSystemTenant() throws Exception {
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));
        await(orchestration.stopWorkload(deployed.getId()));

        await(orchestration.deleteWorkload(deployed.getId()));

        assertEquals("kinotic-system", loki.deletedTenant);
    }

    @Test
    public void stoppedRunReturnsItsRoomOnce() throws Exception {
        nodes.availableNode = registeredNode(NODE_ID, 4, 4096, 10240);
        Workload deployed = await(orchestration.deployWorkload(newWorkload().setCpus(0.5)));
        assertEquals(3.5, nodes.saved.get(NODE_ID).getFreeCpus());

        await(orchestration.stopWorkload(deployed.getId()));

        VmNode node = nodes.saved.get(NODE_ID);
        assertEquals(4, node.getFreeCpus());
        assertEquals(4096, node.getFreeMemoryMb());
        assertEquals(10240, node.getFreeDiskMb());
        assertEquals(WorkloadStatus.STOPPED, workloads.saved.get(deployed.getId()).getStatus());

        // the node's own report of the same end, and a later destroy, return nothing twice
        report(deployed.getId(), WorkloadStatus.STOPPED, 0);
        assertEquals(4, nodes.saved.get(NODE_ID).getFreeCpus());
        await(orchestration.destroyWorkload(deployed.getId()));
        assertEquals(4, nodes.saved.get(NODE_ID).getFreeCpus());
        assertEquals(WorkloadStatus.STOPPED, workloads.saved.get(deployed.getId()).getStatus(), "the record outlives the run");
    }

    @Test
    public void statusReportOfAnEndedRunReturnsItsRoom() throws Exception {
        nodes.availableNode = registeredNode(NODE_ID, 4, 4096, 10240);
        Workload deployed = await(orchestration.deployWorkload(newWorkload().setCpus(2)));
        assertEquals(2, nodes.saved.get(NODE_ID).getFreeCpus());

        report(deployed.getId(), WorkloadStatus.FAILED, 137);

        assertEquals(4, nodes.saved.get(NODE_ID).getFreeCpus());
        assertEquals(4096, nodes.saved.get(NODE_ID).getFreeMemoryMb());
        assertEquals(10240, nodes.saved.get(NODE_ID).getFreeDiskMb());
        assertEquals(137, workloads.saved.get(deployed.getId()).getExitCode());
    }

    @Test
    public void registrationRebuildsTheLedgerFromTheWorkloadRecords() throws Exception {
        nodes.availableNode = registeredNode(NODE_ID, 4, 4096, 10240);
        Workload running = await(orchestration.deployWorkload(newWorkload().setCpus(1)));
        Workload ended = await(orchestration.deployWorkload(newWorkload().setCpus(1)));
        await(orchestration.stopWorkload(ended.getId()));
        // the node comes back with more CPU, and a leak the ledger does not know about
        nodes.saved.get(NODE_ID).setFreeCpus(0);

        VmNode registered = await(nodeOrchestration.registerNode(new VmNodeRegistration().setId(NODE_ID)
                                                                                         .setName("node-1")
                                                                                         .setHostname("host-1")
                                                                                         .setTotalCpus(8)
                                                                                         .setTotalMemoryMb(4096)
                                                                                         .setTotalDiskMb(10240)));

        assertEquals(8 - running.getCpus(), registered.getFreeCpus());
        assertEquals(4096 - running.getMemoryMb(), registered.getFreeMemoryMb());
        assertEquals(10240 - running.getDiskSizeMb(), registered.getFreeDiskMb(), "an ended run holds nothing");
        assertEquals(1, registered.getReservations().size());
        assertEquals(running.getId(), registered.getReservations().get(0).getWorkloadId());
    }

    @Test
    public void pinnedDeployFailsWhenNodeLacksCapacity() {
        registeredNode("node-2", 1, 4096, 10240);

        Future<Workload> run = orchestration.deployWorkload(newWorkload().setNodeId("node-2").setCpus(2));

        assertTrue(run.failed());
        assertNull(vmManager.lastStarted);
    }

    @Test
    public void secretValuesRedactedInRecordButRealOnNode() throws Exception {
        Workload deployed = await(orchestration.deployWorkload(
                newWorkload().setEnvironment(new LinkedHashMap<>(Map.of("LOG_LEVEL", "debug")))
                             .setSecrets(new LinkedHashMap<>(Map.of("GIT_TOKEN", "secret")))));

        // The node received the real secret; the record only ever held the mask, including
        // when the node's start reply (which echoes environment and secrets) was persisted.
        // Plain environment entries persist verbatim.
        assertEquals("secret", vmManager.lastStarted.getSecrets().get("GIT_TOKEN"));
        assertEquals("<redacted>", workloads.saved.get(deployed.getId()).getSecrets().get("GIT_TOKEN"));
        assertEquals("debug", workloads.saved.get(deployed.getId()).getEnvironment().get("LOG_LEVEL"));
        assertEquals("secret", deployed.getSecrets().get("GIT_TOKEN"));
    }

    @Test
    public void secretValuesRedactedWhenStartFails() {
        vmManager.failStartWith = new RuntimeException("node exploded");

        Future<Workload> run = orchestration.deployWorkload(
                newWorkload().setSecrets(new LinkedHashMap<>(Map.of("GIT_TOKEN", "secret"))));

        assertTrue(run.failed());
        Workload stored = workloads.saved.values().iterator().next();
        assertEquals(WorkloadStatus.FAILED, stored.getStatus());
        assertEquals("<redacted>", stored.getSecrets().get("GIT_TOKEN"));
    }

    private VmNode registeredNode(String nodeId, int cpus, int memoryMb, int diskMb) {
        VmNode node = online(new VmNode(nodeId, nodeId, "host-" + nodeId));
        node.setTotalCpus(cpus);
        node.setTotalMemoryMb(memoryMb);
        node.setTotalDiskMb(diskMb);
        node.setFreeCpus(cpus);
        node.setFreeMemoryMb(memoryMb);
        node.setFreeDiskMb(diskMb);
        nodes.saved.put(nodeId, node);
        return node;
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    /** As a registered node that heartbeats stands: taking workloads, reconciled on that, heard just now. */
    private static VmNode online(VmNode node) {
        node.getState().setDesired(ONLINE).setObserved(ONLINE).setGeneration(1).setObservedGeneration(1).setReconciled(true);
        node.setLastSeen(new Date());
        return node;
    }

    /**
     * Returns whether {@link #NODE_ID} carries NODE_UNREACHABLE once that is {@code expected}, or whatever
     * it is when the wait runs out. The mark made by verifyNode's continuation lands on the Vertx context
     * after the call the test awaited returned.
     */
    private boolean awaitNodeUnreachable(boolean expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (nodeUnreachable() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        return nodeUnreachable();
    }

    private boolean nodeUnreachable() {
        return StatusConditions.has(nodes.saved.get(NODE_ID).getState().getConditions(), StatusConditionType.NODE_UNREACHABLE);
    }

    private Date nodeUnreachableSince() {
        return StatusConditions.find(nodes.saved.get(NODE_ID).getState().getConditions(), StatusConditionType.NODE_UNREACHABLE)
                               .map(StatusCondition::since)
                               .orElse(null);
    }

    private boolean unreachable(String workloadId) {
        return StatusConditions.has(workloads.saved.get(workloadId).getState().getConditions(), StatusConditionType.NODE_UNREACHABLE);
    }

    /**
     * Deploys a workload and calls the node's worker with the heartbeat overdue, as the master does,
     * returning the workload once the worker has marked it unreachable.
     */
    private Workload markedUnreachableBySilence() throws Exception {
        properties.getSystemApi().getVmNode().setHeartbeatTimeoutSeconds(1);
        Workload deployed = await(orchestration.deployWorkload(newWorkload()));
        VmNode node = nodes.saved.get(NODE_ID);
        node.setLastSeen(new Date(System.currentTimeMillis() - 10_000));
        nodes.saveSync(node);

        await(nodeOrchestration.reconcile(await(nodes.findById(NODE_ID))));

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
     * Simulates the node pushing a status report, stamped ahead of the record's last save so
     * the report applies even when both happen within the same millisecond.
     */
    private void report(String workloadId, WorkloadStatus status, Integer exitCode) {
        reportAt(workloadId, status, exitCode, System.currentTimeMillis() + 1000);
    }

    /** Simulates the node pushing a status report stamped by the node's own clock. */
    private void reportAt(String workloadId, WorkloadStatus status, Integer exitCode, long updated) {
        WorkloadStatusReport statusReport = new WorkloadStatusReport()
                .setWorkloadId(workloadId)
                .setStatus(status)
                .setExitCode(exitCode)
                .setUpdated(updated);
        nodeOrchestration.reportWorkloadStatus(NODE_ID, List.of(statusReport));
    }
}
