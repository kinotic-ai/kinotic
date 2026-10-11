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
import org.kinotic.system.api.model.workload.VmNode;

/**
 * The node records as the console reads and manages them: every node registered to host
 * workloads, read back with its history. A node is a resource on the platform, so a grant made
 * on the platform reaches every node, and one made on a node reaches that node alone.
 */
@Publish
@AuthzResource(value = VmNodeService.RESOURCE_TYPE, parent = AuthzUtil.PLATFORM_TYPE)
public interface VmNodeService extends IdentifiableCrudService<VmNode, String> {

    /** The resource type a node is in the authorization model. */
    String RESOURCE_TYPE = "vm_node";

    /**
     * Lists what happened to the node, newest first: each change of what it should be and of what it
     * reports, and each mark set beside them, with what caused it.
     * @param nodeId the id of the node
     * @param pageable the page to return
     * @return a future that will complete with a page of ledger entries, empty when the node is not
     * registered
     */
    @AuthzCheck(resourceId = "{nodeId}")
    Future<Page<WatchEvent>> findHistory(String nodeId, Pageable pageable);

}
