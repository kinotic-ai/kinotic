package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;

import java.util.List;

/**
 * Service responsible for orchestrating workload deployment across the cluster.
 * Acts as the intermediary between clients and VmManager instances running on each node.
 * <p>
 * Handles node selection based on available resources, access control,
 * and lifecycle management of workloads.
 * <p>
 * For querying workloads (findById, findAll, search) use {@link WorkloadService} directly.
 */
@Publish
public interface WorkloadOrchestrationService {

    /**
     * Deploys a new workload to an appropriate node in the cluster.
     * The orchestrator selects a node with sufficient resources, persists the workload,
     * and delegates to the VmManager on the selected node. A workload carrying a
     * {@link Workload#getNodeId() nodeId} is deployed to that node instead, failing when the
     * node is not registered, not taking workloads, or lacks the capacity the workload
     * requires.
     * <p>
     * When {@link Workload#isDetached()} is {@code false} the returned future completes only
     * once the run has ended — the workload reached {@link WorkloadStatus#STOPPED} or
     * {@link WorkloadStatus#FAILED}, with its {@link Workload#getExitCode() exit code} set.
     * Otherwise it completes as soon as the workload is started, and the workload runs until
     * it is stopped or destroyed. Whichever way a run ends, the node removes its VM and its
     * room on the node is released; the record stays with the run's outcome, and its logs stay
     * in the log store under its id.
     * <p>
     * A start the node's vm-manager never answered — it took the call and then left the cluster,
     * or the acknowledgement was lost — fails the returned future with
     * {@link org.kinotic.core.api.exceptions.RpcServiceUnavailableException} and leaves the record
     * {@link WorkloadStatus#STARTING}, marked {@link org.kinotic.domain.api.reconcile.StatusConditionType#NODE_UNREACHABLE}
     * and holding its room: the node may be running it, and its next report settles the record
     * either way. A start no vm-manager took is recorded {@link WorkloadStatus#FAILED}.
     *
     * @param workload the workload configuration to deploy
     * @return a future that will complete with the deployed workload (including assigned nodeId and id)
     */
    Future<Workload> deployWorkload(Workload workload);

    /**
     * Stops a running workload.
     * Delegates to the VmManager on the node where the workload is deployed. A stop the vm-manager
     * never answered fails the returned future and leaves the record
     * {@link WorkloadStatus#STOPPING}, marked
     * {@link org.kinotic.domain.api.reconcile.StatusConditionType#NODE_UNREACHABLE} until the node's next
     * report says whether it stopped.
     *
     * @param workloadId the id of the workload to stop
     * @return a future that will complete when the workload has been stopped
     */
    Future<Void> stopWorkload(String workloadId);

    /**
     * Destroys a workload's VM on its node, with its disk, and returns its room. A run still open is
     * recorded {@link WorkloadStatus#STOPPED}. The record stays as the run's outcome, and its logs stay
     * in the log store, until {@link #deleteWorkload(String)}.
     *
     * @param workloadId the id of the workload to destroy
     * @return a future that will complete when the VM has been destroyed
     */
    Future<Void> destroyWorkload(String workloadId);

    /**
     * Deletes a workload's record and every log line it wrote from the organization's log store, the
     * one way either is removed. A run still open on its node — starting, running, or a stop the node
     * has not answered — is refused: stop or destroy it first.
     *
     * @param workloadId the id of the workload to delete
     * @return a future that will complete when the record and its logs are gone, or fail if the run is
     *         still open
     */
    Future<Void> deleteWorkload(String workloadId);

    /**
     * Deletes the given workloads' records and every log line they wrote, as {@link #deleteWorkload}
     * does for one, with the log store asked once per organization for all of its workloads in the
     * batch. Any run still open refuses the whole batch before anything is deleted. The retention
     * sweep calls this for the records whose runs ended longer ago than
     * {@code kinotic.systemApi.workload.retentionDays}.
     *
     * @param workloadIds the ids of the workloads to delete
     * @return a future that will complete when the records and their logs are gone, or fail if any run
     *         is still open
     */
    Future<Void> deleteWorkloads(List<String> workloadIds);

}
