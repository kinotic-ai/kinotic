package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.McpToolDefinitionList;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Elasticsearch repository for {@link ServiceDirectoryEntry}s over the {@code kinotic_service_directory} index.
 * Built on the unscoped {@link AbstractRepository} because system entries have no organization to route by; scope is
 * applied explicitly in the queries here.
 */
@Slf4j
@Component
public class ServiceDirectoryEntryRepository extends AbstractRepository<ServiceDirectoryEntry> {

    // v1 reconciliation reads the whole directory in one page. The directory only holds self-published system
    // services today; page through it once customer contracts (which could reach 100k) start landing.
    private static final int RECONCILE_PAGE_SIZE = 10_000;

    // a name resolution matches at most a handful of entries; more than this only under pathological collision
    private static final int RESOLUTION_PAGE_SIZE = 25;

    private final ObjectMapper objectMapper;

    public ServiceDirectoryEntryRepository(CrudServiceTemplate crudServiceTemplate,
                                           ObjectMapper objectMapper) {
        super("kinotic_service_directory", ServiceDirectoryEntry.class, crudServiceTemplate);
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the contract fields of an entry, creating it when the directory holds none for its id. The
     * liveness fields are left to {@code setOnline}, so a re-registration never disturbs them.
     */
    public CompletableFuture<Void> upsertEntry(ServiceDirectoryEntry entry) {
        @SuppressWarnings("unchecked")
        Map<String, Object> partial = objectMapper.convertValue(entry, Map.class);
        partial.remove("online");
        partial.remove("lastStatusChange");
        // the liveness updater finds entries by search, so this write must be visible to search before the
        // future completes or its first reconcile runs against a directory missing these entries
        return crudServiceTemplate.partialUpsertSync(indexName, entry.getId(), partial, true);
    }

    /**
     * Sets the liveness fields of an existing entry via a partial update touching only {@code online} and
     * {@code lastStatusChange}.
     */
    public CompletableFuture<Void> setOnline(String entryId, boolean online, Instant when) {
        // retry because registration and the other liveness writers merge into this same entry concurrently
        return crudServiceTemplate.partialUpdate(indexName,
                                                 entryId,
                                                 Map.of("online", online, "lastStatusChange", when),
                                                 true);
    }

    /**
     * Resolves the entry for a service address and sets its liveness. Addresses matching no entry are ignored.
     */
    public CompletableFuture<Void> setOnlineByAddress(String serviceAddress, boolean online, Instant when) {
        return findFirst(b -> {
            b.query(termFilter("serviceAddress", serviceAddress));
            b.source(sc -> sc.filter(f -> f.includes("id")));
        }).thenCompose(entry -> entry == null
                ? CompletableFuture.completedFuture(null)
                : setOnline(entry.getId(), online, when));
    }

    /**
     * Corrects the liveness of every entry to match the given snapshot of active service addresses.
     */
    public CompletableFuture<Void> reconcileLiveness(Set<String> activeAddresses, Instant when) {
        return findAll(Pageable.ofSize(RECONCILE_PAGE_SIZE),
                       b -> b.source(sc -> sc.filter(f -> f.includes("id", "serviceAddress", "online"))))
                .thenCompose(page -> {
                    List<CompletableFuture<Void>> updates = new ArrayList<>();
                    for (ServiceDirectoryEntry entry : page.getContent()) {
                        boolean desired = entry.getServiceAddress() != null
                                && activeAddresses.contains(entry.getServiceAddress());
                        if (entry.isOnline() != desired) {
                            updates.add(setOnline(entry.getId(), desired, when));
                        }
                    }
                    return CompletableFuture.allOf(updates.toArray(new CompletableFuture[0]));
                });
    }

    /**
     * Returns the entries in the given scope. A system scope (organizationId null) returns all entries; otherwise the
     * organization (and application, when given) is filtered on, so system entries never match a non-null scope.
     */
    public CompletableFuture<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                              String applicationId,
                                                                              Pageable pageable) {
        Query scopeFilter = scopeFilter(organizationId, applicationId);
        return findAll(pageable, b -> {
            if (scopeFilter != null) {
                b.query(scopeFilter);
            }
        });
    }

    /**
     * Returns the online MCP tools a caller in the given scope may call, flattened from the matching entries. The
     * search {@code _source}-filters to the {@code mcpTools} field so contracts never leave Elasticsearch.
     */
    public CompletableFuture<McpToolDefinitionList> findMcpToolsCallableBy(String organizationId,
                                                                           String applicationId,
                                                                           CursorPageable pageable) {
        // a service exposing MCP tools is callable whether or not it also is "advertised" see @Publish
        Query filter = composeFilter(termFilter("mcpExposed", true),
                                     termFilter("online", true),
                                     zoneVisibilityFilter(organizationId, applicationId));

        return findAll(pageable, b -> {
            b.query(filter);
            // cri is used for service invocation, must never be served in a listing, so we filter it
            b.source(sc -> sc.filter(f -> f.includes("mcpTools").excludes("mcpTools.cri")));
        }).thenApply(page -> {
            List<McpToolDefinition> tools = new ArrayList<>();
            for (ServiceDirectoryEntry entry : page.getContent()) {
                if (entry.getMcpTools() != null) {
                    tools.addAll(entry.getMcpTools());
                }
            }
            // the cursor tracks ENTRIES, so a short entry page is the last page and returns no cursor
            String nextCursor = page instanceof CursorPage<ServiceDirectoryEntry> cursorPage
                    && page.getContent().size() == pageable.getPageSize()
                    ? cursorPage.getCursor()
                    : null;
            return new McpToolDefinitionList().setTools(tools).setNextCursor(nextCursor);
        });
    }

    /**
     * Resolves the online MCP tool with the given name callable by the given scope, completing with {@code null}
     * when no callable tool carries the name. The term on {@code mcpTools.name} narrows to entries carrying
     * the name; the entry may hold other tools, so the flattening keeps only the matching ones.
     */
    public CompletableFuture<McpToolDefinition> findMcpToolByName(String toolName,
                                                                  String organizationId,
                                                                  String applicationId) {
        Query filter = composeFilter(termFilter("mcpTools.name", toolName),
                                     termFilter("mcpExposed", true),
                                     termFilter("online", true),
                                     zoneVisibilityFilter(organizationId, applicationId));
        return findAll(Pageable.ofSize(RESOLUTION_PAGE_SIZE), b -> {
            b.query(filter);
            b.source(sc -> sc.filter(f -> f.includes("mcpTools")));
        }).thenCompose(page -> {
            List<McpToolDefinition> tools = new ArrayList<>();
            for (ServiceDirectoryEntry entry : page.getContent()) {
                if (entry.getMcpTools() != null) {
                    for (McpToolDefinition tool : entry.getMcpTools()) {
                        if (tool.getName().equals(toolName)) {
                            tools.add(tool);
                        }
                    }
                }
            }
            CompletableFuture<McpToolDefinition> ret;
            if (tools.size() > 1) {
                // minted names are unique system wide, so duplicates mean this index holds corrupted data
                // needing manual repair; the detail stays in this log and the caller gets a generic failure
                log.error("MCP tool name '{}' resolved to {} services, OrgId {}, AppId {}, the service directory index is corrupted. Provided by: {}",
                          toolName,
                          tools.size(),
                          organizationId,
                          applicationId,
                          tools.stream().map(McpToolDefinition::getCri).toList());
                ret = CompletableFuture.failedFuture(new IllegalStateException("MCP tool resolution failed for '" + toolName + "'"));
            } else {
                ret = CompletableFuture.completedFuture(tools.isEmpty() ? null : tools.getFirst());
            }
            return ret;
        });
    }

    private Query scopeFilter(String organizationId, String applicationId) {
        Query ret;
        if (organizationId == null) {
            ret = null;
        } else if (applicationId == null) {
            ret = termFilter("organizationId", organizationId);
        } else {
            ret = composeFilter(termFilter("organizationId", organizationId),
                                termFilter("applicationId", applicationId));
        }
        return ret;
    }

    // The listing view of the zone send rules enforced at dispatch time by ZoneRules: system sees all zones,
    // an organization sees os-api + app-api, an application sees its own app.<org>.<app> zone + app-api.
    private Query zoneVisibilityFilter(String organizationId, String applicationId) {
        Query ret;
        if (organizationId == null) {
            ret = null;
        } else {
            List<String> zones = applicationId == null
                    ? List.of(DomainUtil.OS_API_ZONE, DomainUtil.APP_API_ZONE)
                    : List.of(DomainUtil.APP_ZONE_PREFIX + "." + organizationId + "." + applicationId,
                              DomainUtil.APP_API_ZONE);
            ret = Query.of(q -> q.bool(b -> {
                for (String zone : zones) {
                    b.should(termFilter("zone", zone));
                }
                return b.minimumShouldMatch("1");
            }));
        }
        return ret;
    }

}
