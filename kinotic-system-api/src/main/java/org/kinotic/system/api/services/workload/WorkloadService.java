package org.kinotic.system.api.services.workload;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.management.api.model.workload.Workload;

/**
 * The workload records as the console reads and manages them: every run deployed across the
 * cluster, its record kept after the run ends, listed by node and read back with its history.
 * Every function is checked on the platform: reading a record needs {@code can_view_workloads},
 * writing one {@code can_manage_workloads}.
 */
@Publish
@AuthzResource(value = AuthzUtil.PLATFORM_TYPE, resourceId = AuthzUtil.PLATFORM_OBJECT_ID, permission = AuthzUtil.CAN_MANAGE_WORKLOADS)
public interface WorkloadService extends IdentifiableCrudService<Workload, String> {

    @Override
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_WORKLOADS)
    Future<Workload> findById(String id);

    @Override
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_WORKLOADS)
    Future<Long> count();

    @Override
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_WORKLOADS)
    Future<Page<Workload>> findAll(Pageable pageable);

    @Override
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_WORKLOADS)
    Future<Page<Workload>> search(String searchText, Pageable pageable);

    // CrudService declares syncIndex an edit, which only a redeclaration overrides
    @Override
    @AuthzCheck(permission = AuthzUtil.CAN_MANAGE_WORKLOADS)
    Future<Void> syncIndex();

    /**
     * Finds all workloads deployed on the given node.
     * @param nodeId the id of the node to find workloads for
     * @param pageable the page to return
     * @return a future that will complete with a page of workloads
     */
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_WORKLOADS)
    Future<Page<Workload>> findAllForNode(String nodeId, Pageable pageable);

    /**
     * Lists what happened to the workload, newest first: each status its run passed through and each
     * mark set beside it, with what caused it.
     * @param workloadId the workload
     * @param pageable the page to return
     * @return a future that will complete with a page of ledger entries, empty when the workload does
     * not exist
     */
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_WORKLOADS)
    Future<Page<WatchEvent>> findHistory(String workloadId, Pageable pageable);

}
