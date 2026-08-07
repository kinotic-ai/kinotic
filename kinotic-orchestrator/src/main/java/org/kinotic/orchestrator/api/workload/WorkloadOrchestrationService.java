package org.kinotic.orchestrator.api.workload;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.orchestrator.api.model.workload.Workload;
import org.kinotic.orchestrator.api.services.WorkloadService;

import java.util.concurrent.CompletableFuture;

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
     * and delegates to the VmManager on the selected node.
     *
     * @param workload the workload configuration to deploy
     * @return a future that will complete with the deployed workload (including assigned nodeId and id)
     */
    CompletableFuture<Workload> deployWorkload(Workload workload);

    /**
     * Restarts a stopped workload in place on the node it is deployed to. The same VM
     * boots again with its disk state intact and the workload's entrypoint runs again.
     * Fails unless the workload is stopped; a workload stopped with
     * {@link Workload#isAutoRemove()} {@code true} has no VM left to restart.
     *
     * @param workloadId the id of the workload to restart
     * @return a future that will complete with the restarted workload
     */
    CompletableFuture<Workload> restartWorkload(String workloadId);

    /**
     * Stops a running workload.
     * Delegates to the VmManager on the node where the workload is deployed.
     *
     * @param workloadId the id of the workload to stop
     * @return a future that will complete when the workload has been stopped
     */
    CompletableFuture<Void> stopWorkload(String workloadId);

    /**
     * Destroys a workload, removing it from the node and cleaning up all resources.
     *
     * @param workloadId the id of the workload to destroy
     * @return a future that will complete when the workload has been destroyed
     */
    CompletableFuture<Void> destroyWorkload(String workloadId);

}
