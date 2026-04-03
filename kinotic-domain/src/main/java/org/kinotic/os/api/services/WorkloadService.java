package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.os.api.model.workload.Workload;

import java.util.concurrent.CompletableFuture;

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
    CompletableFuture<Page<Workload>> findAllForNode(String nodeId, Pageable pageable);

    /**
     * Counts all workloads deployed on the given node.
     * @param nodeId the id of the node to count workloads for
     * @return a future that will complete with the number of workloads
     */
    CompletableFuture<Long> countForNode(String nodeId);

}
