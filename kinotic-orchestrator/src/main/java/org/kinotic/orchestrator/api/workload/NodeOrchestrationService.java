package org.kinotic.orchestrator.api.workload;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.os.api.model.workload.VmNode;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for tracking and managing VmManager nodes in the cluster.
 * When a vm-manager process starts on a node it registers itself with this service.
 */
@Publish
public interface NodeOrchestrationService {

    /**
     * Registers a VmNode with the orchestrator so it can receive workload deployments.
     * If a node with the same id already exists it will be updated.
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
     * Gets a registered node by id.
     *
     * @param nodeId the id of the node
     * @return a future that will complete with the node, or null if not found
     */
    CompletableFuture<VmNode> getNode(String nodeId);

    /**
     * Lists all registered nodes in the cluster.
     *
     * @param pageable the page to return
     * @return a future that will complete with a page of nodes
     */
    CompletableFuture<Page<VmNode>> listNodes(Pageable pageable);

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
