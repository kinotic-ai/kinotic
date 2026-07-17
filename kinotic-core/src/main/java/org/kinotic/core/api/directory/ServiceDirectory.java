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
     * Returns the entries scoped to the given organization/application. System (OS) entries are system-scoped, so
     * they never match a non-null scope — an organization or application only ever sees what it provides. A system
     * scope (both ids null) returns all entries.
     * @param organizationId the organization scope to filter by, or null for the system scope
     * @param applicationId the application scope to filter by, or null to include all of the organization's entries
     * @param pageable the page settings to use
     * @return a page of entries in the given scope
     */
    CompletableFuture<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                       String applicationId,
                                                                       Pageable pageable);

    /**
     * Returns the online MCP tools the given scope may call, mirroring the zone send rules enforced at call time:
     * a system scope (both ids null) sees all tools, an organization scope sees {@code os-api}- and
     * {@code app-api}-zone tools, and an application scope sees its own {@code app.<org>.<app>}-zone tools plus
     * {@code app-api}-zone tools.
     * @param organizationId the calling scope's organization, or null for a system scope
     * @param applicationId the calling scope's application, or null
     * @param pageable the page settings to use
     * @return a page of callable {@link McpToolDefinition}s, flattened from the matching entries
     */
    CompletableFuture<Page<McpToolDefinition>> findMcpToolsCallableBy(String organizationId,
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
