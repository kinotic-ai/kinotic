package org.kinotic.core.api.directory;

import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.idl.api.directory.SchemaService;
import org.kinotic.idl.api.schema.ServiceDefinition;

import io.vertx.core.Future;

import java.util.List;

/**
 * Keeps track of registered services, the platform's own and the ones an application's runtimes register, and
 * the MCP tools they expose. Every returned {@link Future} is
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
     * The definitions of the platform's own services, the ones no organization owns, which the platform's
     * authorization model is generated from, as the directory holds them now.
     * @return every system-scoped entry's definition
     */
    Future<List<ServiceDefinition>> findSystemDefinitions();

    /**
     * The definitions of one application's services, the ones registered in its scope, which the application's
     * authorization model is generated from, as the directory holds them now.
     * @param organizationId the application's organization
     * @param applicationId the application
     * @return every definition of the application's entries
     */
    Future<List<ServiceDefinition>> findApplicationDefinitions(String organizationId, String applicationId);

    /**
     * The entry of one service, by the id a registration gives it: the service's qualified name with its zone,
     * {@code <zone>~<namespace>.<Name>}.
     * @param entryId the entry's id
     * @return the entry, or null when no service of that id has registered
     */
    Future<ServiceDirectoryEntry> findEntry(String entryId);

    /**
     * Registers a service a runtime of an application serves, as the platform's own services are registered
     * when they start: the entry names the organization and application the service belongs to, its zone,
     * version, description and whether it is advertised, and the definition the runtime declares, with the
     * decorators it declares. The directory derives the checks of a service declaring a resource, as
     * {@link SchemaService#deriveChecks} does, derives the MCP tools its functions declare, and owns the
     * entry's id, address and liveness, which follows the service's registrations on the event bus. The
     * application's store reconciles to a model carrying the service, and an entry equal to the one stored
     * leaves it as it is.
     *
     * @param entry the service to register, as the runtime declares it
     * @return a future that completes once the entry is stored
     * @throws IllegalStateException    when the definition does not derive, as {@link SchemaService#deriveChecks} says,
     *                                  or a function's tool declaration is one MCP cannot serve
     * @throws IllegalArgumentException when the entry names no organization, application or zone, carries no
     *                                  definition, or the definition names no namespace or no name
     */
    Future<Void> register(ServiceDirectoryEntry entry);

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
