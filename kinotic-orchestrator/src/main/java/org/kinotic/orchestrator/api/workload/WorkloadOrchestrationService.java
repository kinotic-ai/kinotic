package org.kinotic.orchestrator.api.workload;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.os.api.model.workload.Workload;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for orchestrating workload deployment across the cluster.
 * Acts as the intermediary between clients and VmManager instances running on each node.
 * <p>
 * Handles node selection based on available resources, access control,
 * and lifecycle management of workloads.
 * <p>
 * For querying workloads (findById, findAll, search) use {@link org.kinotic.os.api.services.WorkloadService} directly.
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
