package org.kinotic.orchestrator.api.workload;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Proxy;
import org.kinotic.core.api.annotations.Scope;
import org.kinotic.orchestrator.api.model.workload.Workload;

import java.util.List;

/**
 * Proxy interface for communicating with a VmManager instance running on a specific node.
 * The VmManager is a Bun process that manages micro VMs via boxlite (or other providers).
 * <p>
 * Each method takes a {@code @Scope String nodeId} parameter that routes the RPC call
 * to the VmManager instance registered with that node's scope. The scope parameter is
 * automatically stripped before the call is dispatched to the remote service.
 */
@Proxy(namespace = "kinotic-ai.vm-manager", name = "VmManager")
public interface VmManagerProxy {

    /**
     * Starts a new workload on the VmManager running on the given node. For a detached
     * workload the future completes as soon as the workload is running; for a non-detached
     * one it completes only once the run has ended, with the final status and exit code.
     * @param nodeId the id of the node to route to
     * @param workload the workload configuration to start
     * @return a future that will complete with the started workload
     */
    Future<Workload> startWorkload(@Scope String nodeId, Workload workload);

    /**
     * Restarts a stopped workload in place on the VmManager running on the given node.
     * The same VM boots again with its disk state intact and the workload's entrypoint
     * runs again. Resolves at boot or at run end the same way as
     * {@link #startWorkload(String, Workload)}.
     * @param nodeId the id of the node to route to
     * @param workloadId the id of the workload to restart
     * @return a future that will complete with the restarted workload
     */
    Future<Workload> restartWorkload(@Scope String nodeId, String workloadId);

    /**
     * Stops a running workload on the VmManager running on the given node.
     * @param nodeId the id of the node to route to
     * @param workloadId the id of the workload to stop
     * @return a future that will complete when the workload has been stopped
     */
    Future<Void> stopWorkload(@Scope String nodeId, String workloadId);

    /**
     * Destroys a workload on the VmManager running on the given node.
     * @param nodeId the id of the node to route to
     * @param workloadId the id of the workload to destroy
     * @return a future that will complete when the workload has been destroyed
     */
    Future<Void> destroyWorkload(@Scope String nodeId, String workloadId);

    /**
     * Gets the current state of a workload from the VmManager running on the given node.
     * @param nodeId the id of the node to route to
     * @param workloadId the id of the workload
     * @return a future that will complete with the workload
     */
    Future<Workload> getWorkload(@Scope String nodeId, String workloadId);

    /**
     * Lists all workloads managed by the VmManager running on the given node.
     * @param nodeId the id of the node to route to
     * @return a future that will complete with the list of workloads
     */
    Future<List<Workload>> listWorkloads(@Scope String nodeId);

}
