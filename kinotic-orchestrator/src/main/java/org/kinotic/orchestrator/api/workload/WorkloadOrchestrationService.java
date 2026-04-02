package org.kinotic.orchestrator.api.workload;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.os.api.model.workload.VmNode;
import org.kinotic.os.api.model.workload.Workload;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for orchestrating workload deployment across the cluster.
 * Acts as the intermediary between clients and VmManager instances running on each node.
 * <p>
 * Handles node selection based on available resources, access control,
 * and lifecycle management of workloads.
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

    /**
     * Gets the current state of a workload.
     *
     * @param workloadId the id of the workload
     * @return a future that will complete with the workload
     */
    CompletableFuture<Workload> getWorkload(String workloadId);

    /**
     * Lists all workloads across the cluster.
     *
     * @param pageable the page to return
     * @return a future that will complete with a page of workloads
     */
    CompletableFuture<Page<Workload>> listWorkloads(Pageable pageable);

    /**
     * Lists all workloads deployed on a specific node.
     *
     * @param nodeId the id of the node
     * @param pageable the page to return
     * @return a future that will complete with a page of workloads
     */
    CompletableFuture<Page<Workload>> listWorkloadsForNode(String nodeId, Pageable pageable);

    /**
     * Registers a VmNode with the orchestrator so it can receive workload deployments.
     *
     * @param node the node to register
     * @return a future that will complete with the registered node
     */
    CompletableFuture<VmNode> registerNode(VmNode node);

    /**
     * Removes a node from the orchestrator. The node must have no active workloads.
     *
     * @param nodeId the id of the node to deregister
     * @return a future that will complete when the node has been removed
     */
    CompletableFuture<Void> deregisterNode(String nodeId);

    /**
     * Lists all registered nodes in the cluster.
     *
     * @param pageable the page to return
     * @return a future that will complete with a page of nodes
     */
    CompletableFuture<Page<VmNode>> listNodes(Pageable pageable);

}
