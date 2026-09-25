package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.model.workload.WorkloadStatus;

import java.util.Date;

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
     * Counts the workloads made by the given record whose run failed since the given time: what a
     * worker's restart backoff counts.
     * @param parent the record the workloads were made by
     * @param since epoch milliseconds
     * @return a future that will complete with the number of failed runs
     */
    Future<Long> countFailedFor(WatchedParent parent, long since);

    /**
     * Finds the workloads whose run ended before the cutoff, oldest first.
     * @param cutoff the moment a run must have ended before
     * @param pageable the page to return
     * @return a future that will complete with a page of workloads
     */
    Future<Page<Workload>> findEndedBefore(Date cutoff, Pageable pageable);

    /**
     * Lists what happened to the workload, newest first: each status its run passed through and each
     * mark set beside it, with what caused it.
     * @param workloadId the workload
     * @param pageable the page to return
     * @return a future that will complete with a page of ledger entries, empty when the workload does
     * not exist
     */
    Future<Page<WatchEvent>> findHistory(String workloadId, Pageable pageable);

    /**
     * Records the status and exit code of the workload's run and enters the change in the ledger,
     * leaving every other field as it is; visible to search on completion.
     * @param workloadId the workload whose run is reported
     * @param status the run's status
     * @param exitCode the run's exit code, or null to leave the recorded one as it is
     * @param source what caused it, for the ledger: the node that reported, the operator, the call
     * @return a future that will complete when the run is recorded
     */
    Future<Void> updateRunSync(String workloadId, WorkloadStatus status, Integer exitCode, String source);

    /**
     * Records the end of the workload's run, its terminal status and exit code, and enters the change in
     * the ledger, leaving every other field as it is; a run that has already ended keeps its outcome.
     * Visible to search on completion.
     * @param workloadId the workload whose run ended
     * @param status the terminal status
     * @param exitCode the run's exit code, or null to leave the recorded one as it is
     * @param source what caused it, for the ledger: the node that reported, the operator, the call
     * @return a future that will complete with true when this call ended the run and false when it had
     * ended already
     */
    Future<Boolean> endRunSync(String workloadId, WorkloadStatus status, Integer exitCode, String source);

    /**
     * Marks the workload with the given condition, beside the status its node reports, and enters
     * it in the ledger. A workload already carrying a condition of that type keeps it as it is.
     * @param workloadId the workload to mark
     * @param condition the condition to set
     * @param source what caused it, for the ledger
     * @return a future that will complete with true when the condition was set, false when the
     *         workload already carried one of its type
     */
    Future<Boolean> setCondition(String workloadId, StatusCondition condition, String source);

    /**
     * Clears the workload's condition of the given type and enters it in the ledger. A workload
     * carrying none is left as it is.
     * @param workloadId the workload to clear
     * @param type the type to clear
     * @param source what caused it, for the ledger
     * @return a future that will complete with true when a condition was cleared, false when the
     *         workload carried none of the type
     */
    Future<Boolean> clearCondition(String workloadId, StatusConditionType type, String source);

}
