package org.kinotic.test.tests.system;

import io.vertx.core.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeStatus;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.system.api.services.VmNodeOrchestrationService;
import org.kinotic.system.api.services.VmNodeService;
import org.kinotic.system.api.workload.VmNodeRegistration;
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

        VmNode chosen = await(vmNodeService.findAvailableNode(2, 2048, 5120));

        Assertions.assertNotNull(chosen, "a node with room should have been found");
        Assertions.assertEquals("placement-roomy", chosen.getId());
    }

    @Test
    public void returnsNullWhenEveryNodeIsShortOnASingleResource() throws Exception {
        // enough cpu and memory, not enough disk — each resource has to be filtered, not just the first
        node("placement-thin-disk", 8, 8192, 20480, 8, 8192, 1024);
        indexNodes();

        Assertions.assertNull(await(vmNodeService.findAvailableNode(2, 2048, 5120)));
    }

    @Test
    public void skipsNodesThatAreNotTakingWorkloads() throws Exception {
        VmNode draining = node("placement-draining", 8, 8192, 20480, 8, 8192, 20480);
        draining.setStatus(new VmNodeStatus(VmNodeStatusType.DRAINING, "under maintenance"));
        await(vmNodeService.save(draining));
        indexNodes();

        Assertions.assertNull(await(vmNodeService.findAvailableNode(1, 1024, 1024)));
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

        VmNode chosen = await(vmNodeService.findAvailableNode(2, 2048, 5120));

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
        Assertions.assertEquals(VmNodeStatusType.ONLINE, registered.getStatus().getType(),
                                "a freshly registered node must be online to be placeable");
        indexNodes();

        VmNode chosen = await(vmNodeOrchestrationService.findAvailableNode(1, 2048, 1024));

        Assertions.assertNotNull(chosen, "placement found no node despite one registering with 16 cpus free");
        Assertions.assertEquals("placement-live", chosen.getId());
        Assertions.assertNotNull(chosen.getWorkloadDataDir(),
                                 "resolveTarget rejects a node that advertises no workload data directory");
    }

    /**
     * A node that comes back with different hardware must not look wholly free while its workloads
     * are still running, or placement will oversubscribe it.
     */
    @Test
    public void reRegisteringWithNewCapacityKeepsRunningWorkloadsAccountedFor() throws Exception {
        VmNode registered = await(vmNodeOrchestrationService.registerNode(registration("placement-rereg", 8, 8192, 20480)));
        created.add(registered.getId());
        Assertions.assertEquals(8, registered.getAvailableCpus());

        registered.setAvailableCpus(5);
        await(vmNodeService.saveSync(registered));

        VmNode grown = await(vmNodeOrchestrationService.registerNode(registration("placement-rereg", 16, 8192, 20480)));

        Assertions.assertEquals(16, grown.getTotalCpus());
        Assertions.assertEquals(13, grown.getAvailableCpus(), "the 3 allocated vCPUs should survive the capacity change");
    }

    private VmNode node(String id, int totalCpus, int totalMemoryMb, int totalDiskMb,
                        int availableCpus, int availableMemoryMb, int availableDiskMb) throws Exception {
        VmNode node = new VmNode(id, id, "host-" + id);
        node.setTotalCpus(totalCpus)
            .setTotalMemoryMb(totalMemoryMb)
            .setTotalDiskMb(totalDiskMb)
            .setAvailableCpus(availableCpus)
            .setAvailableMemoryMb(availableMemoryMb)
            .setAvailableDiskMb(availableDiskMb)
            .setStatus(new VmNodeStatus(VmNodeStatusType.ONLINE, null));
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
