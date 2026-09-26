package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeState;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.system.api.services.workload.VmNodeOrchestrationService;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;
import org.kinotic.system.api.services.workload.VmNodeService;
import org.kinotic.system.api.services.workload.WorkloadService;
import org.kinotic.system.api.model.workload.VmNodeRegistration;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Exercises node placement against a real Elasticsearch index: capacity and liveness are
 * expressed as a query, so the mapping, the stored availability, and the filters have to agree
 * for a node to come back.
 */
@SpringBootTest
public class VmNodePlacementTests extends KinoticTestBase {

    @Autowired
    private VmNodeService vmNodeService;

    @Autowired
    private VmNodeOrchestrationService vmNodeOrchestrationService;

    @Autowired
    private WorkloadService workloadService;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    public void removeCreatedNodes() throws Exception {
        for (String id : created) {
            await(vmNodeService.deleteById(id));
        }
        created.clear();
        await(vmNodeService.syncIndex());
    }

    @Test
    public void findsANodeWithRoomAndSkipsTheFullOnes() throws Exception {
        node("placement-full", 8, 8192, 20480, 0, 0, 0);
        node("placement-roomy", 8, 8192, 20480, 4, 4096, 10240);
        indexNodes();

        VmNode chosen = await(vmNodeOrchestrationService.findAvailableNode(2, 2048, 5120));

        Assertions.assertNotNull(chosen, "a node with room should have been found");
        Assertions.assertEquals("placement-roomy", chosen.getId());
    }

    @Test
    public void returnsNullWhenEveryNodeIsShortOnASingleResource() throws Exception {
        // enough cpu and memory, not enough disk — each resource has to be filtered, not just the first
        node("placement-thin-disk", 8, 8192, 20480, 8, 8192, 1024);
        indexNodes();

        Assertions.assertNull(await(vmNodeOrchestrationService.findAvailableNode(2, 2048, 5120)));
    }

    @Test
    public void skipsNodesThatAreNotTakingWorkloads() throws Exception {
        VmNode draining = node("placement-draining", 8, 8192, 20480, 8, 8192, 20480);
        // a node reporting a problem is not in its desired state, and placement selects on that
        draining.getState().setObserved(new VmNodeState(VmNodeStatusType.DRAINING)).setReconciled(false);
        draining.setHealthMessage("under maintenance");
        await(vmNodeService.save(draining));
        indexNodes();

        Assertions.assertNull(await(vmNodeOrchestrationService.findAvailableNode(1, 1024, 1024)));
    }

    /**
     * The one node with room sits well past the first page of online nodes. Selecting the page and
     * filtering it in memory finds nothing here; only a query that filters on capacity does.
     */
    @Test
    public void findsANodeBeyondTheFirstPageOfOnlineNodes() throws Exception {
        for (int i = 0; i < 120; i++) {
            node("placement-crowd-" + i, 8, 8192, 20480, 0, 0, 0);
        }
        node("placement-needle", 8, 8192, 20480, 4, 4096, 10240);
        indexNodes();

        VmNode chosen = await(vmNodeOrchestrationService.findAvailableNode(2, 2048, 5120));

        Assertions.assertNotNull(chosen, "the only node with room is past the first page of online nodes");
        Assertions.assertEquals("placement-needle", chosen.getId());
    }

    /**
     * The call ProjectDeployJobDefinitionFactory.resolveTarget makes, against a node registered the
     * way a live vm-manager registers one: a probe Workload's defaults (1 vcpu, 1024MB disk) with the
     * sync memory override. A deploy that never gets past "Resolve deployment target" is this
     * returning nothing — or not returning.
     */
    @Test
    public void findsANodeThatJustRegisteredItselfTheWayAVmManagerDoes() throws Exception {
        VmNode registered = await(vmNodeOrchestrationService.registerNode(
                registration("placement-live", 16, 131072, 1902788)));
        created.add(registered.getId());
        Assertions.assertTrue(registered.getState().isReconciled(),
                              "a freshly registered node must be in its desired state to be placeable");
        indexNodes();

        VmNode chosen = await(vmNodeOrchestrationService.findAvailableNode(1, 2048, 1024));

        Assertions.assertNotNull(chosen, "placement found no node despite one registering with 16 cpus free");
        Assertions.assertEquals("placement-live", chosen.getId());
        Assertions.assertNotNull(chosen.getWorkloadDataDir(),
                                 "resolveTarget rejects a node that advertises no workload data directory");
    }

    /**
     * A node that comes back with different hardware must not look wholly free while its workloads
     * are still running, or placement will oversubscribe it: the ledger is rebuilt from the workload
     * records, one entry per run that has not ended.
     */
    @Test
    public void reRegisteringWithNewCapacityKeepsPlacedWorkloadsAccountedFor() throws Exception {
        VmNode registered = await(vmNodeOrchestrationService.registerNode(registration("placement-rereg", 8, 8192, 20480)));
        created.add(registered.getId());
        Assertions.assertEquals(8, registered.getFreeCpus());
        Workload running = await(workloadService.saveSync(workload("placement-rereg", 2.5, 1024, 2048, WorkloadStatus.RUNNING)));
        Workload ended = await(workloadService.saveSync(workload("placement-rereg", 1, 1024, 2048, WorkloadStatus.STOPPED)));
        await(workloadService.syncIndex());

        VmNode grown;
        try {
            grown = await(vmNodeOrchestrationService.registerNode(registration("placement-rereg", 16, 8192, 20480)));
        } finally {
            await(workloadService.deleteById(running.getId()));
            await(workloadService.deleteById(ended.getId()));
        }

        Assertions.assertEquals(16, grown.getTotalCpus());
        Assertions.assertEquals(13.5, grown.getFreeCpus(), "the running workload's CPU should survive the capacity change");
        Assertions.assertEquals(8192 - 1024, grown.getFreeMemoryMb());
        Assertions.assertEquals(20480 - 2048, grown.getFreeDiskMb(), "an ended run holds nothing");
    }

    private static Workload workload(String nodeId, double cpus, int memoryMb, int diskMb, WorkloadStatus status) {
        Workload workload = new Workload("placement-" + status.name().toLowerCase(), "alpine:latest");
        workload.setNodeId(nodeId);
        workload.setCpus(cpus);
        workload.setMemoryMb(memoryMb);
        workload.setDiskSizeMb(diskMb);
        workload.setStatus(status);
        return workload;
    }

    private VmNode node(String id, int totalCpus, int totalMemoryMb, int totalDiskMb,
                        double freeCpus, int freeMemoryMb, int freeDiskMb) throws Exception {
        VmNode node = new VmNode(id, id, "host-" + id);
        node.setTotalCpus(totalCpus)
            .setTotalMemoryMb(totalMemoryMb)
            .setTotalDiskMb(totalDiskMb)
            .setFreeCpus(freeCpus)
            .setFreeMemoryMb(freeMemoryMb)
            .setFreeDiskMb(freeDiskMb);
        // as a registered node that heartbeats stands: taking workloads, and reconciled on that
        node.getState().setDesired(new VmNodeState(VmNodeStatusType.ONLINE))
            .setObserved(new VmNodeState(VmNodeStatusType.ONLINE))
            .setGeneration(1)
            .setObservedGeneration(1)
            .setReconciled(true);
        created.add(id);
        return await(vmNodeService.save(node));
    }

    private VmNodeRegistration registration(String id, int totalCpus, int totalMemoryMb, int totalDiskMb) {
        return new VmNodeRegistration().setId(id)
                                       .setName(id)
                                       .setHostname("host-" + id)
                                       .setTotalCpus(totalCpus)
                                       .setTotalMemoryMb(totalMemoryMb)
                                       .setTotalDiskMb(totalDiskMb)
                                       .setWorkloadDataDir("/var/lib/kinotic/" + id);
    }

    /** Individual saves skip the refresh; one index sync afterwards makes the whole fixture searchable. */
    private void indexNodes() throws Exception {
        await(vmNodeService.syncIndex());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
