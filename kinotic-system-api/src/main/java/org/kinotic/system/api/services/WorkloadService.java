package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.workload.Workload;

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
     * Counts all workloads deployed on the given node.
     * @param nodeId the id of the node to count workloads for
     * @return a future that will complete with the number of workloads
     */
    Future<Long> countForNode(String nodeId);

}
