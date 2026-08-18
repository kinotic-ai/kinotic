package org.kinotic.orchestrator.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.orchestrator.api.model.workload.VmNode;
import org.kinotic.orchestrator.api.services.VmNodeService;

import org.kinotic.orchestrator.api.workload.VmNodeRegistration;
import org.kinotic.orchestrator.api.workload.WorkloadStatusReport;
import java.util.List;

/**
 * Service responsible for tracking and managing VmManager nodes in the cluster.
 * When a vm-manager process starts on a node it registers itself with this service.
 * Nodes must send periodic heartbeats to remain in ONLINE status.
 * <p>
 * For querying nodes (findById, findAll, search) use {@link VmNodeService} directly.
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
    Future<VmNode> registerNode(VmNodeRegistration registration);

    /**
     * Heartbeat from a running vm-manager node.
     * Updates the node's {@code lastSeen} timestamp to indicate it is still alive.
     *
     * @param nodeId the id of the node sending the heartbeat
     * @return a future that will complete with the updated node, or fail if the node is not registered
     */
    Future<VmNode> heartbeat(String nodeId);

    /**
     * Applies a node's report of the guarantees it can still make. A node reporting problems
     * is moved to {@link org.kinotic.orchestrator.api.model.workload.VmNodeStatus#DRAINING} so
     * the orchestrator stops placing workloads on it, and back to ONLINE once it reports none.
     * The workloads already there keep running: a node that stopped enforcing a limit is unfit
     * to take on more, not required to drop what it has.
     *
     * @param nodeId the id of the node sending the report
     * @param problems what the node can no longer guarantee, empty when it is fit
     * @return a future that will complete with the updated node
     */
    Future<VmNode> reportNodeHealth(String nodeId, List<String> problems);

    /**
     * Applies a node's report of its workloads' actual statuses. The vm-manager sends a
     * report whenever a workload changes state on the node — including transitions the
     * orchestrator did not initiate, such as recovery after a vm-manager restart — and a
     * periodic full snapshot for reconciliation. Reports older than the workload's last
     * transition, or for workloads that no longer exist, are ignored.
     *
     * @param nodeId the id of the reporting node
     * @param reports one report per workload
     * @return a future that will complete when the reports have been applied
     */
    Future<Void> reportWorkloadStatus(String nodeId, List<WorkloadStatusReport> reports);

    /**
     * Removes a node from the orchestrator. The node must have no active workloads.
     *
     * @param nodeId the id of the node to deregister
     * @return a future that will complete when the node has been removed
     */
    Future<Void> deregisterNode(String nodeId);

    /**
     * Finds a node with sufficient resources to host a workload with the given requirements.
     *
     * @param requiredCpus the number of vCPUs required
     * @param requiredMemoryMb the amount of memory required in megabytes
     * @param requiredDiskMb the amount of disk space required in megabytes
     * @return a future that will complete with a suitable node, or null if none available
     */
    Future<VmNode> findAvailableNode(int requiredCpus, int requiredMemoryMb, int requiredDiskMb);

}
