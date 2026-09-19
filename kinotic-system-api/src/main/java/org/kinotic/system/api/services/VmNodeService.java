package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeStatus;
import org.kinotic.system.api.model.workload.WorkloadReservation;

/**
 * Service for managing {@link VmNode} entities.
 * Tracks available nodes in the cluster that can host workloads.
 */
@Publish
public interface VmNodeService extends IdentifiableCrudService<VmNode, String> {

    /**
     * Finds a node with sufficient resources to host a workload with the given requirements.
     * @param requiredCpus the CPU required, in cores
     * @param requiredMemoryMb the amount of memory required in megabytes
     * @param requiredDiskMb the amount of disk space required in megabytes
     * @return a future that will complete with a suitable node, or null if none available
     */
    Future<VmNode> findAvailableNode(double requiredCpus, int requiredMemoryMb, int requiredDiskMb);

    /**
     * Sets a node's {@link VmNode#getStatus() status}, leaving every other field of the record as it is,
     * and completes once the change is visible to {@link #findAvailableNode}.
     * @param nodeId the id of the node to update
     * @param status the node's new status
     * @return a future that will complete when the status is stored, or fail if the node is not registered
     */
    Future<Void> updateStatusSync(String nodeId, VmNodeStatus status);

    /**
     * Reserves a workload's room on a node for its run: takes it from the node's unallocated capacity if
     * the node has it, atomically against every other reservation and release on the node, and completes
     * once the change is visible to {@link #findAvailableNode}. A workload that already holds the room
     * keeps it, and one that holds only its disk from an earlier run takes its CPU and memory again.
     * @param nodeId the id of the node to reserve on
     * @param reservation the workload and the room its run needs
     * @return a future that will complete with true when the workload holds the room and false when the
     * node no longer has it, or fail if the node is not registered
     */
    Future<Boolean> reserveSync(String nodeId, WorkloadReservation reservation);

    /**
     * Returns the CPU and memory of a workload whose run has ended to the node's unallocated capacity,
     * keeping the disk its VM still occupies, atomically against every other reservation and release on
     * the node, and completes once the change is visible to {@link #findAvailableNode}. A workload not
     * holding a run is left as it is.
     * @param nodeId the id of the node to release on
     * @param workloadId the id of the workload whose run ended
     * @return a future that will complete when the resources are released, or fail if the node is not registered
     */
    Future<Void> releaseRunSync(String nodeId, String workloadId);

    /**
     * Returns everything a workload holds on a node to its unallocated capacity, atomically against every
     * other reservation and release on the node, and completes once the change is visible to
     * {@link #findAvailableNode}. A workload holding nothing is left as it is.
     * @param nodeId the id of the node to release on
     * @param workloadId the id of the workload to release
     * @return a future that will complete when the resources are released, or fail if the node is not registered
     */
    Future<Void> releaseSync(String nodeId, String workloadId);

}
