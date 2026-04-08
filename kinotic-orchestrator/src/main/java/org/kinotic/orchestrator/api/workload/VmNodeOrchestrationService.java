package org.kinotic.orchestrator.api.workload;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.os.api.model.workload.VmNode;
import org.kinotic.orchestrator.api.workload.VmNodeRegistration;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for tracking and managing VmManager nodes in the cluster.
 * When a vm-manager process starts on a node it registers itself with this service.
 * Nodes must send periodic heartbeats to remain in ONLINE status.
 * <p>
 * For querying nodes (findById, findAll, search) use {@link org.kinotic.os.api.services.VmNodeService} directly.
 */
@Publish
public interface VmNodeOrchestrationService {

    /**
     * Registers a VmNode with the orchestrator so it can receive workload deployments.
     * If a node with the same id already exists it will be updated with the new resource info.
     *
     * @param registration the node registration info
     * @return a future that will complete with the registered node
     */
    CompletableFuture<VmNode> registerNode(VmNodeRegistration registration);

    /**
     * Heartbeat from a running vm-manager node.
     * Updates the node's {@code lastSeen} timestamp to indicate it is still alive.
     *
     * @param nodeId the id of the node sending the heartbeat
     * @return a future that will complete with the updated node, or fail if the node is not registered
     */
    CompletableFuture<VmNode> heartbeat(String nodeId);

    /**
     * Removes a node from the orchestrator. The node must have no active workloads.
     *
     * @param nodeId the id of the node to deregister
     * @return a future that will complete when the node has been removed
     */
    CompletableFuture<Void> deregisterNode(String nodeId);

    /**
     * Finds a node with sufficient resources to host a workload with the given requirements.
     *
     * @param requiredCpus the number of vCPUs required
     * @param requiredMemoryMb the amount of memory required in megabytes
     * @param requiredDiskMb the amount of disk space required in megabytes
     * @return a future that will complete with a suitable node, or null if none available
     */
    CompletableFuture<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb);

}
