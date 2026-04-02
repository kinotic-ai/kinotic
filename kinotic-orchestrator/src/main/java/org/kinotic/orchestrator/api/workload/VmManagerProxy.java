package org.kinotic.orchestrator.api.workload;

import org.kinotic.core.api.annotations.Proxy;
import org.kinotic.core.api.annotations.Scope;
import org.kinotic.os.api.model.workload.Workload;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Proxy interface for communicating with a VmManager instance running on a specific node.
 * The VmManager is a Bun process that manages micro VMs via boxlite (or other providers).
 * <p>
 * Each method takes a {@code @Scope String nodeId} parameter that routes the RPC call
 * to the VmManager instance registered with that node's scope. The scope parameter is
 * automatically stripped before the call is dispatched to the remote service.
 */
@Proxy(namespace = "org.kinotic.os.api.services", name = "VmManager")
public interface VmManagerProxy {

    /**
     * Starts a new workload on the VmManager running on the given node.
     * @param nodeId the id of the node to route to
     * @param workload the workload configuration to start
     * @return a future that will complete with the started workload
     */
    CompletableFuture<Workload> startWorkload(@Scope String nodeId, Workload workload);

    /**
     * Stops a running workload on the VmManager running on the given node.
     * @param nodeId the id of the node to route to
     * @param workloadId the id of the workload to stop
     * @return a future that will complete when the workload has been stopped
     */
    CompletableFuture<Void> stopWorkload(@Scope String nodeId, String workloadId);

    /**
     * Destroys a workload on the VmManager running on the given node.
     * @param nodeId the id of the node to route to
     * @param workloadId the id of the workload to destroy
     * @return a future that will complete when the workload has been destroyed
     */
    CompletableFuture<Void> destroyWorkload(@Scope String nodeId, String workloadId);

    /**
     * Gets the current state of a workload from the VmManager running on the given node.
     * @param nodeId the id of the node to route to
     * @param workloadId the id of the workload
     * @return a future that will complete with the workload
     */
    CompletableFuture<Workload> getWorkload(@Scope String nodeId, String workloadId);

    /**
     * Lists all workloads managed by the VmManager running on the given node.
     * @param nodeId the id of the node to route to
     * @return a future that will complete with the list of workloads
     */
    CompletableFuture<List<Workload>> listWorkloads(@Scope String nodeId);

}
