package org.kinotic.core.api.directory;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy encapsulating how directory entries are persisted and queried for a particular backend. A concrete
 * strategy owns its backend's data access; all directory behavior (capture, verification, liveness maintenance) is
 * provided by the {@link ServiceDirectory} built over it, whose bean exists only when a strategy bean does.
 */
public interface ServiceDirectoryStrategy {

    /**
     * Finds an entry by id.
     * @param id the entry id
     * @return a {@link CompletableFuture} containing the entry, or null when none exists
     */
    CompletableFuture<ServiceDirectoryEntry> findById(String id);

    /**
     * Upserts an entry, leaving the liveness fields ({@code online}, {@code lastStatusChange}) untouched.
     * @param entry the entry to upsert
     * @return a {@link CompletableFuture} completing when the entry is stored
     */
    CompletableFuture<Void> upsertEntry(ServiceDirectoryEntry entry);

    /**
     * Sets the liveness fields of the entry with the given id.
     * @param entryId the entry id
     * @param online the liveness state
     * @param when the time of the state change
     * @return a {@link CompletableFuture} completing when the entry is updated
     */
    CompletableFuture<Void> setOnline(String entryId, boolean online, Instant when);

    /**
     * Sets the liveness fields of the entry with the given service address.
     * @param serviceAddress the service address of the entry
     * @param online the liveness state
     * @param when the time of the state change
     * @return a {@link CompletableFuture} completing when the entry is updated
     */
    CompletableFuture<Void> setOnlineByAddress(String serviceAddress, boolean online, Instant when);

    /**
     * Corrects the liveness of every entry against the full set of currently active service addresses: entries
     * whose address is present become online, all others become offline.
     * @param activeAddresses the complete snapshot of service addresses with registered listeners
     * @param when the time of the correction
     * @return a {@link CompletableFuture} completing when all entries are corrected
     */
    CompletableFuture<Void> reconcileLiveness(Set<String> activeAddresses, Instant when);

    /**
     * Returns the entries scoped to the given organization/application. A system scope (both ids null) returns all
     * entries.
     * @param organizationId the organization scope to filter by, or null for the system scope
     * @param applicationId the application scope to filter by, or null to include all of the organization's entries
     * @param pageable the page settings to use
     * @return a page of entries in the given scope
     */
    CompletableFuture<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                       String applicationId,
                                                                       Pageable pageable);

    /**
     * Returns the online MCP tools callable by the given scope per the zone send rules.
     * @param organizationId the calling scope's organization, or null for a system scope
     * @param applicationId the calling scope's application, or null
     * @param pageable the page settings to use
     * @return a page of callable {@link McpToolDefinition}s, flattened from the matching entries
     */
    CompletableFuture<Page<McpToolDefinition>> findMcpToolsCallableBy(String organizationId,
                                                                      String applicationId,
                                                                      Pageable pageable);

}
