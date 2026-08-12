package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.domain.api.utils.DomainUtil;

import java.util.concurrent.CompletableFuture;

/**
 * The service directory for platform operators: every registered service contract with its
 * verified liveness, plus the on-demand liveness corrections. Published in the system zone,
 * which only SYSTEM participants may address.
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

    /**
     * Verifies the cluster-wide registration state of the given service address and writes the
     * verified liveness.
     *
     * @param serviceAddress the service address to verify
     * @return a {@link CompletableFuture} completing when the verified state is stored
     */
    CompletableFuture<Void> verifyLiveness(String serviceAddress);

    /**
     * Corrects the liveness of every entry against a fresh snapshot of the cluster's active
     * service addresses.
     *
     * @return a {@link CompletableFuture} completing when all entries are corrected
     */
    CompletableFuture<Void> reconcileLiveness();
}
