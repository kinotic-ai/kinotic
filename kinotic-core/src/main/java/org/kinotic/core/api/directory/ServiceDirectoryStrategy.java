package org.kinotic.core.api.directory;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;

import java.time.Instant;
import java.util.Set;
import io.vertx.core.Future;

/**
 * Strategy encapsulating how directory entries are persisted and queried for a particular backend. A concrete
 * strategy owns its backend's data access and must complete every returned {@link Future} on the Vert.x context
 * of the caller; all directory behavior (contract conversion, verification, liveness maintenance) is provided by
 * the {@link ServiceDirectory} built over it, whose bean exists only when a strategy bean does.
 */
public interface ServiceDirectoryStrategy {

    /**
     * Upserts an entry, leaving the liveness fields ({@code online}, {@code lastStatusChange}) untouched.
     * @param entry the entry to upsert
     * @return a {@link Future} completing when the entry is stored
     */
    Future<Void> upsertEntry(ServiceDirectoryEntry entry);

    /**
     * Sets the liveness fields of the entry with the given id.
     * @param entryId the entry id
     * @param online the liveness state
     * @param when the time of the state change
     * @return a {@link Future} completing when the entry is updated
     */
    Future<Void> setOnline(String entryId, boolean online, Instant when);

    /**
     * Sets the liveness fields of the entry with the given service address.
     * @param serviceAddress the service address of the entry
     * @param online the liveness state
     * @param when the time of the state change
     * @return a {@link Future} completing when the entry is updated
     */
    Future<Void> setOnlineByAddress(String serviceAddress, boolean online, Instant when);

    /**
     * Corrects the liveness of every entry against the full set of currently active service addresses: entries
     * whose address is present become online, all others become offline.
     * @param activeAddresses the complete snapshot of service addresses with registered listeners
     * @param when the time of the correction
     * @return a {@link Future} completing when all entries are corrected
     */
    Future<Void> reconcileLiveness(Set<String> activeAddresses, Instant when);

    /**
     * Resolves the online MCP tool with the given name callable by the given scope.
     * @param toolName the MCP tool name to resolve
     * @param organizationId the calling scope's organization, or null for a system scope
     * @param applicationId the calling scope's application, or null
     * @return a {@link Future} completing with the callable tool carrying the name, or null when none does
     */
    Future<McpToolDefinition> findMcpToolByName(String toolName,
                                                           String organizationId,
                                                           String applicationId);

    /**
     * Returns the entries scoped to the given organization/application. A system scope (both ids null) returns all
     * entries.
     * @param organizationId the organization scope to filter by, or null for the system scope
     * @param applicationId the application scope to filter by, or null to include all of the organization's entries
     * @param pageable the page settings to use
     * @return a page of entries in the given scope
     */
    Future<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                       String applicationId,
                                                                       Pageable pageable);

    /**
     * Returns the online MCP tools callable by the given scope per the zone send rules.
     * @param organizationId the calling scope's organization, or null for a system scope
     * @param applicationId the calling scope's application, or null
     * @param pageable the page settings to use
     * @return the page of callable {@link McpToolDefinition}s, carrying a {@code nextCursor} when more exist
     */
    Future<McpToolDefinitionList> findMcpToolsCallableBy(String organizationId,
                                                         String applicationId,
                                                         Pageable pageable);

}
