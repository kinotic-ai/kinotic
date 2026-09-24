package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.reconcile.StatusCondition;
import org.kinotic.core.api.reconcile.StatusConditionType;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;

/**
 * Service for managing {@link Workload} entities.
 * Tracks all workloads that have been deployed across the cluster.
 */
@Publish
public interface WorkloadService extends IdentifiableCrudService<Workload, String> {

    /**
     * Finds all workloads deployed on the given node.
     * @param nodeId the id of the node to find workloads for
     * @param pageable the page to return
     * @return a future that will complete with a page of workloads
     */
    Future<Page<Workload>> findAllForNode(String nodeId, Pageable pageable);

    /**
     * Counts the workloads on the given node whose run has not ended: the ones still holding a VM
     * there.
     * @param nodeId the id of the node to count workloads for
     * @return a future that will complete with the number of running workloads
     */
    Future<Long> countRunningForNode(String nodeId);

    /**
     * Records the status and exit code of the workload's run, leaving every other field as it is;
     * visible to search on completion.
     * @param workloadId the workload whose run is reported
     * @param status the run's status
     * @param exitCode the run's exit code, or null to leave the recorded one as it is
     * @return a future that will complete when the run is recorded
     */
    Future<Void> updateRunSync(String workloadId, WorkloadStatus status, Integer exitCode);

    /**
     * Marks the workload with the given condition, beside the status its node reports. A workload
     * already carrying a condition of that type keeps it as it is.
     * @param workloadId the workload to mark
     * @param condition the condition to set
     * @return a future that will complete with true when the condition was set, false when the
     *         workload already carried one of its type
     */
    Future<Boolean> setCondition(String workloadId, StatusCondition condition);

    /**
     * Clears the workload's condition of the given type. A workload carrying none is left as it is.
     * @param workloadId the workload to clear
     * @param type the type to clear
     * @return a future that will complete with true when a condition was cleared, false when the
     *         workload carried none of the type
     */
    Future<Boolean> clearCondition(String workloadId, StatusConditionType type);

}
