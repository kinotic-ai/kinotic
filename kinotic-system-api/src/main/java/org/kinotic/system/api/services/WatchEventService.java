package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.WatchEvent;

/**
 * The ledger of every watched record on the platform, as the console reads it.
 */
@Publish
@AuthzResource(value = AuthzUtil.PLATFORM_TYPE, resourceId = AuthzUtil.PLATFORM_OBJECT_ID)
public interface WatchEventService {

    /**
     * Lists what happened to every watched record, newest first: each change of what a record should
     * be and of what it reports, each mark set beside them, and each status a run passed through, with
     * what caused it.
     *
     * @param pageable the page to return
     * @return a future that will complete with a page of ledger entries
     */
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_CLUSTER)
    Future<Page<WatchEvent>> findAll(Pageable pageable);

}
