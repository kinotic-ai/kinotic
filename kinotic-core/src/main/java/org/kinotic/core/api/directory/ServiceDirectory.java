package org.kinotic.core.api.directory;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.service.ServiceIdentifier;

import java.util.concurrent.CompletableFuture;

/**
 * Keeps track of registered service contracts and the MCP tools they expose.
 * <p>
 * {@code kinotic-core} defines this API but ships no implementation: the registration path resolves it optionally,
 * so a standalone core deployment with no implementation bean does nothing at all. The Elasticsearch implementation
 * lives in {@code kinotic-domain}.
 *
 * Created by navid on 2019-06-11.
 */
public interface ServiceDirectory {

    /**
     * Registers a published service with the directory. What is captured and stored, when the work happens (it may
     * be batched), and whether any work happens at all is the implementation's decision; failures are handled and
     * reported by the directory.
     * @param serviceIdentifier the identifier the service registered under
     * @param serviceInterface the {@code @Publish} interface being registered
     */
    void register(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface);

    /**
     * Notifies the directory that the calling node no longer provides the service. Other nodes may still provide
     * it, so how liveness is updated is the implementation's decision. Entries are never deleted; a
     * known-but-offline service is a feature.
     * @param serviceIdentifier the identifier the service registered under
     * @param serviceInterface the {@code @Publish} interface being unregistered
     */
    void unregister(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface);

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

    /**
     * Verifies the cluster-wide registration state of the given service address and writes the verified liveness.
     * @param serviceAddress the service address to verify
     * @return a {@link CompletableFuture} completing when the verified state is stored
     */
    CompletableFuture<Void> verifyLiveness(String serviceAddress);

    /**
     * Corrects the liveness of every entry against a fresh snapshot of the cluster's active service addresses.
     * @return a {@link CompletableFuture} completing when all entries are corrected
     */
    CompletableFuture<Void> reconcileLiveness();

}
