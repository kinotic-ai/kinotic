package org.kinotic.core.api.directory;

import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.idl.api.schema.ServiceDefinition;

import io.vertx.core.Future;

import java.util.List;

/**
 * Keeps track of registered service contracts and the MCP tools they expose. Every returned {@link Future} is
 * completed on the Vert.x context of the caller.
 * <p>
 * {@code kinotic-core} defines this API but ships no implementation: the registration path resolves it optionally,
 * so a standalone core deployment with no implementation bean does nothing at all.
 * Created by navid on 2019-06-11.
 */
public interface ServiceDirectory {

    /**
     * Returns the entries scoped to the given organization/application. System (OS) entries are system-scoped, so
     * they never match a non-null scope — an organization or application only ever sees what it provides. A system
     * scope (both ids null) returns all entries.
     * @param organizationId the organization scope to filter by, or null for the system scope
     * @param applicationId the application scope to filter by, or null to include all of the organization's entries
     * @param pageable the page settings to use
     * @return a page of entries in the given scope
     */
    Future<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                            String applicationId,
                                                            Pageable pageable);

    /**
     * The converted contracts of the platform's own services, the ones no organization owns, which the platform's
     * authorization model is generated from, as the directory holds them now.
     * @return every system-scoped entry's contract
     */
    Future<List<ServiceDefinition>> findSystemContracts();

    /**
     * Resolves the online MCP tool with the given name that the given scope may call, using the same visibility
     * rules as {@link #findMcpToolsCallableBy}. Tool names are unique system wide.
     * @param toolName the MCP tool name to resolve
     * @param organizationId the calling scope's organization, or null for a system scope
     * @param applicationId the calling scope's application, or null
     * @return a {@link Future} completing with the callable tool carrying the name, or null when none does
     */
    Future<McpToolDefinition> findMcpToolByName(String toolName,
                                                String organizationId,
                                                String applicationId);

    /**
     * Returns the online MCP tools the given scope may call through this server, mirroring the zone send rules
     * enforced at call time: a system scope (both ids null) sees all tools, an organization scope sees
     * {@code management-api}- and {@code app-api}-zone tools and those of its own applications under
     * {@code app.<org>}, and an application scope sees its own {@code app.<org>.<app>}-zone tools plus
     * {@code app-api}-zone tools, each zone with its sub-zones, and each scope only in the zones this server's
     * {@link org.kinotic.core.api.event.ZonePartitioningService} reaches.
     * @param organizationId the calling scope's organization, or null for a system scope
     * @param applicationId the calling scope's application, or null
     * @param pageable the {@link CursorPageable} to use, because the MCP spec only supports cursor.
     * @return the page of callable {@link McpToolDefinition}s, carrying a {@code nextCursor} when more exist
     */
    Future<McpToolDefinitionList> findMcpToolsCallableBy(String organizationId,
                                                         String applicationId,
                                                         CursorPageable pageable);

    /**
     * Corrects the liveness of every entry that disagrees with a fresh snapshot of the cluster's active service
     * addresses.
     * @return a {@link Future} completing when every such entry is corrected
     */
    Future<Void> reconcileLiveness();

    /**
     * Registers a published service with the directory. A service registered while the context starts is
     * converted with every other in one session once all singletons exist, and one whose contract cannot be
     * created, because a type does not convert or a declaration on it does not resolve, fails the context's
     * refresh; a service registered later is converted on its own and rejected with the cause. When the entry
     * is stored, and whether the service is stored at all, is the implementation's decision; a storage failure
     * is reported by the directory.
     * @param serviceIdentifier the identifier the service registered under
     * @param serviceInterface the {@code @Publish} interface being registered
     * @param serviceImplementation the class implementing the interface, an AOP proxy class is unwrapped;
     *                              generic bindings and annotations resolve against its methods
     */
    void register(ServiceIdentifier serviceIdentifier, Class<?> serviceInterface, Class<?> serviceImplementation);

    /**
     * Reports that a caller could not reach the service at the given CRI.
     * Implementations re-check current registrations and correct the liveness state to the verified truth;
     * this is an invalidation trigger, never a blind offline write.
     * @param cri the CRI that could not be reached, scoped or not: the service it names is what is verified
     * @return a {@link Future} completing when the report has been accepted
     */
    Future<Void> reportUnreachable(String cri);

    /**
     * Notifies the directory that the calling node no longer provides the service. Other nodes may still provide
     * it, so how liveness is updated is the implementation's decision. Entries are never deleted; a
     * known-but-offline service is a feature. An identifier this node never registered is ignored.
     * @param serviceIdentifier the identifier the service registered under
     */
    void unregister(ServiceIdentifier serviceIdentifier);

    /**
     * Verifies the cluster-wide registration state of the given service and writes the verified liveness: the
     * service is online while any instance of it listens, on its shared address or under any scope.
     * @param serviceAddress any address of the service, with or without a scope
     * @return a {@link Future} completing when the verified state is stored
     */
    Future<Void> verifyLiveness(String serviceAddress);

}
