package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.system.api.model.workload.VmNode;
import org.kinotic.system.api.model.workload.VmNodeState;

/**
 * Service for managing {@link VmNode} entities.
 * Tracks available nodes in the cluster that can host workloads.
 */
@Publish
public interface VmNodeService extends IdentifiableCrudService<VmNode, String> {

    /**
     * Finds a node in its desired state — taking workloads, reachable, not being deregistered — with
     * sufficient resources to host a workload with the given requirements.
     * @param requiredCpus the CPU required, in cores
     * @param requiredMemoryMb the amount of memory required in megabytes
     * @param requiredDiskMb the amount of disk space required in megabytes
     * @return a future that will complete with a suitable node, or null if none available
     */
    Future<VmNode> findAvailableNode(double requiredCpus, int requiredMemoryMb, int requiredDiskMb);

    /**
     * Writes what a node reports at registration and the ledger rebuilt from its workload records —
     * name, hostname, provider, totals, free capacity, reservations, data directory — and stamps
     * {@code lastSeen}, creating the record for a node registering for the first time and leaving the
     * node's state and health message as they are. Completes once the change is visible to
     * {@link #findAvailableNode}.
     * @param node the node's inventory, under its id
     * @return a future that will complete when the inventory is stored
     */
    Future<Void> recordInventorySync(VmNode node);

    /**
     * Stamps a node's {@code lastSeen} and writes the reason it gives for not taking workloads, null
     * when it gives none, leaving every other field as it is.
     * @param nodeId the id of the node that sent the heartbeat
     * @param healthMessage what the node can no longer guarantee, or null when it is fit
     * @return a future that will complete when the heartbeat is stored, or fail if the node is not registered
     */
    Future<Void> recordHeartbeat(String nodeId, String healthMessage);

    /**
     * Writes what a node should be, and enters the change in the ledger; completes once the change is
     * visible to {@link #findAvailableNode}. An intent already in place leaves the node as it is.
     * @param nodeId the id of the node
     * @param desired what the node should be
     * @param source what caused it, for the ledger
     * @return a future that will complete with the node as written, or fail if the node is not registered
     */
    Future<VmNode> updateDesired(String nodeId, VmNodeState desired, String source);

    /**
     * Writes what a node reports it is and which generation of intent that answers, and enters the
     * change in the ledger; completes once the change is visible to {@link #findAvailableNode}. A
     * report equal to the last leaves the node as it is.
     * @param nodeId the id of the node
     * @param observed what the node is
     * @param seen the generation of intent the report answers
     * @param source what caused it, for the ledger
     * @return a future that will complete when the report is stored, or fail if the node is not registered
     */
    Future<Void> reportObserved(String nodeId, VmNodeState observed, long seen, String source);

    /**
     * Marks the node with the given condition, beside what it reports, and enters it in the ledger;
     * completes once the change is visible to {@link #findAvailableNode}. A node already carrying a
     * condition of that type keeps it as it is.
     * @param nodeId the id of the node to mark
     * @param condition the condition to set
     * @param source what caused it, for the ledger
     * @return a future that will complete with true when the condition was set, false when the node
     *         already carried one of its type, or fail if the node is not registered
     */
    Future<Boolean> setCondition(String nodeId, StatusCondition condition, String source);

    /**
     * Clears the node's condition of the given type and enters it in the ledger; completes once the
     * change is visible to {@link #findAvailableNode}. A node carrying none is left as it is.
     * @param nodeId the id of the node to clear
     * @param type the type to clear
     * @param source what caused it, for the ledger
     * @return a future that will complete with true when a condition was cleared, false when the node
     *         carried none of the type, or fail if the node is not registered
     */
    Future<Boolean> clearCondition(String nodeId, StatusConditionType type, String source);

    /**
     * Writes that the node should be removed, and enters it in the ledger. Deletion is intent: the
     * node's worker records the runs still open on it and deletes the record. A deletion already
     * asked for is left as it is.
     * @param nodeId the id of the node to remove
     * @param source what caused it, for the ledger
     * @return a future that will complete when the request is stored, or fail if the node is not registered
     */
    Future<Void> requestDeletion(String nodeId, String source);

    /**
     * Reserves a workload's room on a node for its run: takes the CPU, memory and disk the workload is
     * sized for from the node's unallocated capacity if the node has it, atomically against every other
     * reservation and release on the node, and completes once the change is visible to
     * {@link #findAvailableNode}.
     * @param nodeId the id of the node to reserve on
     * @param workload the workload whose run needs the room
     * @return a future that will complete with true when the room is the workload's and false when the
     * node does not have it, or fail if the node is not registered
     */
    Future<Boolean> reserveSync(String nodeId, Workload workload);

    /**
     * Returns a workload's room, the CPU, memory and disk it is sized for, to a node's unallocated
     * capacity, atomically against every other reservation and release on the node, and completes once
     * the change is visible to {@link #findAvailableNode}. A run's room is returned once, when the run
     * ends; a second return credits the node again.
     * @param nodeId the id of the node to release on
     * @param workload the workload whose run held the room
     * @return a future that will complete when the room is returned, or fail if the node is not registered
     */
    Future<Void> releaseSync(String nodeId, Workload workload);

}
