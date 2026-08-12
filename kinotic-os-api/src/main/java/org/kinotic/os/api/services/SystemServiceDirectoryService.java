package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.domain.api.utils.DomainUtil;

import java.util.concurrent.CompletableFuture;

/**
 * The service directory for platform operators: every registered service contract with the
 * liveness state the directory maintains. Published in the system zone, which only SYSTEM
 * participants may address.
 */
@Publish
@Zone(DomainUtil.SYSTEM_ZONE)
public interface SystemServiceDirectoryService {

    /**
     * Returns every directory entry, regardless of owning scope.
     *
     * @param pageable the page settings to use
     * @return a page of {@link ServiceDirectoryEntry}s
     */
    CompletableFuture<Page<ServiceDirectoryEntry>> findEntries(Pageable pageable);

}
