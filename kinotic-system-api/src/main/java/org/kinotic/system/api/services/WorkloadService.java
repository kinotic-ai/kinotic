package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.management.api.model.workload.Workload;

/**
 * The workload records as the console reads and manages them: every run deployed across the
 * cluster, its record kept after the run ends, listed by node and read back with its history.
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
     * Lists what happened to the workload, newest first: each status its run passed through and each
     * mark set beside it, with what caused it.
     * @param workloadId the workload
     * @param pageable the page to return
     * @return a future that will complete with a page of ledger entries, empty when the workload does
     * not exist
     */
    Future<Page<WatchEvent>> findHistory(String workloadId, Pageable pageable);

}
