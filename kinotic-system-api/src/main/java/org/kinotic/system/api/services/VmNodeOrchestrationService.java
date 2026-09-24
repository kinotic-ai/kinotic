package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeStatusType;
import org.kinotic.system.api.workload.VmNodeRegistration;
import org.kinotic.system.api.workload.WorkloadStatusReport;

import java.util.List;

/**
 * Service responsible for tracking and managing VmManager nodes in the cluster.
 * When a vm-manager process starts on a node it registers itself with this service.
 * Nodes must send periodic heartbeats to stay reachable: a node silent past the heartbeat timeout
 * carries {@link org.kinotic.core.api.reconcile.StatusConditionType#NODE_UNREACHABLE} on its
 * {@link VmNode#getState() state} until its next heartbeat, and takes no workloads meanwhile.
 * <p>
 * For querying nodes (findById, findAll, search) use {@link VmNodeService} directly.
 */
@Publish
public interface VmNodeOrchestrationService {

    /**
     * Registers a VmNode with the orchestrator so it can receive workload deployments. If a node with
     * the same id already exists it is updated with the new resource info. A registered node's
     * desired state is to take workloads, and the registration is the node's word that it is up, so a
     * node registering again after a silence is reachable at once.
     *
     * @param registration the node registration info
     * @return a future that will complete with the registered node
     */
    Future<VmNode> registerNode(VmNodeRegistration registration);

    /**
     * Heartbeat from a running vm-manager node, carrying what it can still guarantee. Updates the
     * node's {@code lastSeen} timestamp to indicate it is still alive, and clears the
     * {@code NODE_UNREACHABLE} condition a silence or an undelivered call left on it.
     * <p>
     * A node reporting problems reports itself {@link VmNodeStatusType#DRAINING}, so the orchestrator
     * stops placing workloads on it, and {@link VmNodeStatusType#ONLINE} once it reports none. The
     * workloads already there keep running: a node that stopped enforcing a limit is unfit to
     * take on more, not required to drop what it has.
     *
     * @param nodeId the id of the node sending the heartbeat
     * @param problems what the node can no longer guarantee, empty when it is fit
     * @return a future that will complete with the updated node, or fail if the node is not registered
     */
    Future<VmNode> heartbeat(String nodeId, List<String> problems);

    /**
     * Applies a node's report of its workloads' actual statuses. The vm-manager sends a
     * report whenever a workload changes state on the node — including transitions the
     * orchestrator did not initiate, such as recovery after a vm-manager restart — and a
     * periodic full snapshot for reconciliation. A run only moves forward, so a report of a state
     * earlier than the record's, or for a workload that no longer exists, is ignored — except for a
     * workload marked {@link org.kinotic.core.api.reconcile.StatusConditionType#NODE_UNREACHABLE}:
     * the report ends the silence the condition was inferred from, so it is applied whatever it says
     * and clears the condition.
     *
     * @param nodeId the id of the reporting node
     * @param reports one report per workload
     * @return a future that will complete when the reports have been applied
     */
    Future<Void> reportWorkloadStatus(String nodeId, List<WorkloadStatusReport> reports);

    /**
     * Asks for a node's removal. A node marked {@code NODE_UNREACHABLE} is taken as not coming back:
     * every run still open on it is recorded {@code FAILED} and the node is removed with its capacity
     * ledger. Any other node must have no running workloads. The removal is intent: the node's worker
     * carries it out once the request is recorded.
     *
     * @param nodeId the id of the node to deregister
     * @return a future that will complete when the removal has been asked for, or fail if the node is
     *         not registered or still runs workloads
     */
    Future<Void> deregisterNode(String nodeId);

    /**
     * Checks a node a call to its vm-manager could not reach. A node whose vm-manager holds no service
     * registration is marked {@code NODE_UNREACHABLE} at once, so no workload is placed on it before
     * the heartbeat timeout would have noticed; a registered one is left as it is.
     * @param nodeId the id of the node to check
     * @return a future that completes when the check has been applied
     */
    Future<Void> verifyNode(String nodeId);

    /**
     * Finds a node with sufficient resources to host a workload with the given requirements.
     *
     * @param requiredCpus the CPU required, in cores
     * @param requiredMemoryMb the amount of memory required in megabytes
     * @param requiredDiskMb the amount of disk space required in megabytes
     * @return a future that will complete with a suitable node, or null if none available
     */
    Future<VmNode> findAvailableNode(double requiredCpus, int requiredMemoryMb, int requiredDiskMb);

}
