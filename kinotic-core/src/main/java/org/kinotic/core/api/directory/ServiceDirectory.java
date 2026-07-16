package org.kinotic.core.api.directory;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;

import java.util.concurrent.CompletableFuture;

/**
 * Keeps track of registered service contracts and the MCP tools they expose.
 * <p>
 * {@code kinotic-core} defines this API but ships no implementation: capture resolves it optionally, so a standalone
 * core deployment with no implementation bean does no directory work at all. The Elasticsearch implementation lives in
 * {@code kinotic-domain}.
 *
 * Created by navid on 2019-06-11.
 */
public interface ServiceDirectory {

    /**
     * Upserts the contract fields of an entry. Implementations must not let this clobber the liveness fields
     * ({@code online}, {@code lastStatusChange}) they maintain independently.
     * @param entry the entry to upsert
     * @return a {@link CompletableFuture} completing when the entry is stored
     */
    CompletableFuture<Void> register(ServiceDirectoryEntry entry);

    /**
     * Marks the entry with the given id offline. Entries are never deleted; a known-but-offline service is a feature.
     * @param entryId the id of the entry to mark offline
     * @return a {@link CompletableFuture} completing when the entry is marked offline
     */
    CompletableFuture<Void> unregister(String entryId);

    /**
     * Returns the entries owned by the given scope. System (OS) entries are never returned, so an owner only ever sees
     * what it provides.
     * @param organizationId the owning organization to filter by
     * @param applicationId the owning application to filter by, or null to include all of the organization's entries
     * @param pageable the page settings to use
     * @return a page of owned entries
     */
    CompletableFuture<Page<ServiceDirectoryEntry>> findByOwner(String organizationId,
                                                              String applicationId,
                                                              Pageable pageable);

    /**
     * Returns the MCP-exposed, online entries a caller in the given scope may call: a system caller
     * (both ids null) sees all, an organization caller sees {@code os-api}-zone entries, and an application caller sees
     * its own application's entries plus {@code os-api}-zone entries. Mirrors the zone send rules enforced at call time.
     * @param organizationId the caller's organization, or null for a system caller
     * @param applicationId the caller's application, or null
     * @param pageable the page settings to use
     * @return a page of callable entries
     */
    CompletableFuture<Page<ServiceDirectoryEntry>> findMcpToolsForCaller(String organizationId,
                                                                        String applicationId,
                                                                        Pageable pageable);

    /**
     * Reports that a caller could not reach the service at the given CRI. Implementations re-check current
     * registrations and correct the liveness state to the verified truth; this is an invalidation trigger, never a
     * blind offline write.
     * @param cri the CRI that could not be reached
     * @return a {@link CompletableFuture} completing when the report has been accepted
     */
    CompletableFuture<Void> reportUnreachable(String cri);

}
