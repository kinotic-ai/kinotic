package org.kinotic.core.api.directory;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;

import java.time.Instant;
import java.util.Set;

/**
 * Strategy encapsulating how directory entries are persisted and queried for a particular backend.
 * A concrete strategy owns its backend's data access and must complete every returned {@link Future} on the Vert.x context
 * of the caller; all directory behavior (contract conversion, verification, liveness maintenance) is provided by
 * the {@link ServiceDirectory} built over it, whose bean exists only when a strategy bean does.
 */
public interface ServiceDirectoryStrategy {

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
     * Returns the entries of the platform's own services, the ones no organization owns.
     * @param pageable the page settings to use
     * @return a page of the system-scoped entries
     */
    Future<Page<ServiceDirectoryEntry>> findSystemEntries(Pageable pageable);

    /**
     * The entry with the given id, or null when there is none.
     * @param entryId the entry's id, {@code <zone>~<namespace>.<Name>}
     * @return the entry or null
     */
    Future<ServiceDirectoryEntry> findEntry(String entryId);

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
     * Returns the online MCP tools callable by the given scope per the zone send rules.
     * @param organizationId the calling scope's organization, or null for a system scope
     * @param applicationId the calling scope's application, or null
     * @param pageable the page settings to use
     * @return the page of callable {@link McpToolDefinition}s, carrying a {@code nextCursor} when more exist
     */
    Future<McpToolDefinitionList> findMcpToolsCallableBy(String organizationId,
                                                         String applicationId,
                                                         CursorPageable pageable);

    /**
     * Corrects the liveness of the entries that disagree with the full set of currently active service addresses:
     * an entry whose address is present becomes online, one whose address is absent becomes offline, and one
     * already in the state the snapshot gives it is left as it is. The entries written take the snapshot's time as
     * their last verification, so a liveness write observed before the snapshot cannot land on them after it.
     * @param activeAddresses the complete snapshot of service addresses with registered listeners, with no scope
     * @param when the time the snapshot was taken
     * @return a {@link Future} completing when every disagreeing entry is corrected
     */
    Future<Void> reconcileLiveness(Set<String> activeAddresses, Instant when);

    /**
     * Sets the liveness of the entry with the given id as observed at the given time; an observation
     * earlier than the entry's last verification leaves the entry as it is.
     * @param entryId the entry id
     * @param online the liveness state
     * @param when the time the liveness was observed
     * @return a {@link Future} completing when the entry is updated
     */
    Future<Void> setOnline(String entryId, boolean online, Instant when);

    /**
     * Sets the liveness of the entry with the given service address as observed at the given time; an
     * observation earlier than the entry's last verification leaves the entry as it is.
     * @param serviceAddress the service address of the entry, with no scope
     * @param online the liveness state
     * @param when the time the liveness was observed
     * @return a {@link Future} completing when the entry is updated
     */
    Future<Void> setOnlineByAddress(String serviceAddress, boolean online, Instant when);

    /**
     * Upserts an entry, leaving the liveness fields ({@code online}, {@code lastStatusChange},
     * {@code livenessVerifiedAt}) untouched.
     * @param entry the entry to upsert
     * @return a {@link Future} completing when the entry is stored
     */
    Future<Void> upsertEntry(ServiceDirectoryEntry entry);

}
