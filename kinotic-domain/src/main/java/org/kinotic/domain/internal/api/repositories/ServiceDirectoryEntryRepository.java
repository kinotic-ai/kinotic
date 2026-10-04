package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.McpToolDefinitionList;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.ServiceDirectoryRecord;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Elasticsearch repository for the service directory over the {@code kinotic_service_directory} index, whose
 * documents are {@link ServiceDirectoryRecord}s: each entry with what the platform keeps on a watched record. A
 * contract write marks the entry for the reconcile master, which tells the authorization store the entry belongs
 * to, so the store regenerates its model. Built on the unscoped watched repository because system entries have no
 * organization to route by; scope is applied explicitly in the queries here.
 */
@Slf4j
@Component
public class ServiceDirectoryEntryRepository extends AbstractWatchedRepository<ServiceDirectoryRecord> {

    private static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.SERVICE, "kinotic_service_directory");
    // a liveness reconcile reads the directory by cursor, three fields of each entry at a time
    private static final int LIVENESS_PAGE_SIZE = 1_000;
    // Written by the liveness owner only, so a contract write carries none of them
    private static final Set<String> LIVENESS_FIELDS = Set.of("online", "lastStatusChange", "livenessVerifiedAt");
    // A liveness write is an observation made at a time, and two writers observe the same entry: the
    // node whose services stop and the node whose services start, a verify and a reconcile. The entry
    // keeps the time of the latest observation applied and declines one observed earlier that lands
    // later, so the last write to land is never the earlier word
    private static final String SET_ONLINE = """
            if (ctx._source.livenessVerifiedAt != null && ctx._source.livenessVerifiedAt > params.verifiedAt) {
                ctx.op = 'noop';
            } else {
                if (ctx._source.online != params.online) {
                    ctx._source.online = params.online;
                    ctx._source.lastStatusChange = params.lastStatusChange;
                }
                ctx._source.livenessVerifiedAt = params.verifiedAt;
            }
            """;
    // A contract write is one shard operation that stores the contract and marks the entry for the reconcile
    // master, so the store the entry belongs to regenerates its model. The hash of the stored contract tells an
    // unchanged write apart, so a node starting with what the directory already holds marks nothing; an entry
    // being created holds no hash yet, so its first write is never declined
    private static final String PUBLISH_CONTRACT = WatchedStateRepository.STATE_FUNCTIONS + """
            if (ctx._source.contractHash == params.contractHash) {
                ctx.op = 'noop';
            } else {
                for (def field : params.contract.entrySet()) {
                    ctx._source[field.getKey()] = field.getValue();
                }
                ctx._source.contractHash = params.contractHash;
                def s = state(ctx._source);
                s.parent = params.parent;
                touched(ctx._source, s, params);
            }
            """;
    // a name resolution matches at most a handful of entries; more than this only under pathological collision
    private static final int RESOLUTION_PAGE_SIZE = 25;

    private final ObjectMapper objectMapper;
    private final ZonePartitioningService zonePartitioningService;

    public ServiceDirectoryEntryRepository(CrudServiceTemplate crudServiceTemplate,
                                           WatchedStateRepository watchedStateRepository,
                                           WatchEventRepository watchEventRepository,
                                           ObjectMapper objectMapper,
                                           ZonePartitioningService zonePartitioningService) {
        super(WATCHED, ServiceDirectoryRecord.class, crudServiceTemplate, watchedStateRepository, watchEventRepository);
        this.objectMapper = objectMapper;
        this.zonePartitioningService = zonePartitioningService;
    }

    @Override
    public String scopeOf(ServiceDirectoryRecord record) {
        return record.getOrganizationId();
    }

    /**
     * Publishes an entry's contract, leaving the liveness fields untouched so a re-registration never clobbers
     * the {@code online} state the liveness owner maintains. The write marks the entry for the reconcile master,
     * names the authorization store the entry belongs to as its parent, and is entered in the ledger; a
     * contract equal to the one stored leaves the entry as it is. Visible to search on completion.
     */
    public Future<Void> upsertEntry(ServiceDirectoryEntry entry) {
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = objectMapper.convertValue(entry, Map.class);
        contract.keySet().removeAll(LIVENESS_FIELDS);
        String contractHash = hash(contract);
        Map<String, Object> params = Map.of("contract", contract,
                                            "contractHash", contractHash,
                                            "parent", storeOf(entry).value());
        return watchedStateRepository.write(document(entry.getId()), PUBLISH_CONTRACT, params,
                                            new WatchedChange(WatchEventKind.CONTRACT_PUBLISHED, "service registration",
                                                              "Published " + entry.getId(), Map.of("contractHash", contractHash)),
                                            contract)
                                     .mapEmpty();
    }

    // The store an entry's contract belongs to: its application's, or the platform's for every other entry
    private static WatchedParent storeOf(ServiceDirectoryEntry entry) {
        return entry.getApplicationId() == null
                ? new WatchedParent(WatchedType.AUTHZ_STORE, null, AuthzStore.PLATFORM)
                : new WatchedParent(WatchedType.AUTHZ_STORE, entry.getOrganizationId(), entry.getApplicationId());
    }

    // SHA-256 of the contract with its keys sorted at every level, so two writes of one contract hash the same
    private String hash(Map<String, Object> contract) {
        String canonical = objectMapper.writer()
                                       .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                                       .writeValueAsString(contract);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                                         .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Sets the liveness of an existing entry as observed at the given time, touching only the liveness
     * fields; an observation earlier than the entry's last verification leaves it as it is. Visible to
     * search on completion.
     */
    public Future<Void> setOnline(String entryId, boolean online, Instant when) {
        Map<String, Object> params = Map.of("online", online,
                                            "lastStatusChange", when,
                                            "verifiedAt", when.toEpochMilli());
        return crudServiceTemplate.scriptedUpdateSync(indexName, entryId, SET_ONLINE, params).mapEmpty();
    }

    /**
     * Resolves the entry for a service address and sets its liveness. Addresses matching no entry are ignored.
     */
    public Future<Void> setOnlineByAddress(String serviceAddress, boolean online, Instant when) {
        return findFirst(b -> {
            b.query(termFilter("serviceAddress", serviceAddress));
            b.source(sc -> sc.filter(f -> f.includes("id")));
        }).compose(entry -> entry == null
                ? Future.succeededFuture()
                : setOnline(entry.getId(), online, when));
    }

    /**
     * Corrects the liveness of every entry that disagrees with the given snapshot of active service addresses,
     * stamping each entry it writes with the snapshot's time as its last verification.
     */
    public Future<Void> reconcileLiveness(Set<String> activeAddresses, Instant when) {
        return reconcileLiveness(activeAddresses, when, null);
    }

    // Reads the directory page by page from the cursor, so a directory of any size is read whole, and writes
    // only an entry whose liveness disagrees with the snapshot
    private Future<Void> reconcileLiveness(Set<String> activeAddresses, Instant when, String cursor) {
        return findAll(Pageable.create(cursor, LIVENESS_PAGE_SIZE, Sort.by("id")),
                       b -> b.source(sc -> sc.filter(f -> f.includes("id", "serviceAddress", "online"))))
                .compose(page -> {
                    List<Future<Void>> updates = new ArrayList<>();
                    for (ServiceDirectoryEntry entry : page.getContent()) {
                        boolean desired = entry.getServiceAddress() != null
                                && activeAddresses.contains(entry.getServiceAddress());
                        if (desired != entry.isOnline()) {
                            updates.add(setOnline(entry.getId(), desired, when));
                        }
                    }
                    Future<Void> written = Future.all(updates).mapEmpty();
                    String next = page instanceof CursorPage<ServiceDirectoryRecord> cursorPage ? cursorPage.getCursor() : null;
                    return page.getContent().size() < LIVENESS_PAGE_SIZE || next == null
                            ? written
                            : written.compose(v -> reconcileLiveness(activeAddresses, when, next));
                });
    }

    /**
     * Returns the entries in the given scope. A system scope (organizationId null) returns all entries; otherwise the
     * organization (and application, when given) is filtered on, so system entries never match a non-null scope.
     */
    public Future<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                   String applicationId,
                                                                   Pageable pageable) {
        Query scopeFilter = scopeFilter(organizationId, applicationId);
        return findAll(pageable, b -> {
            if (scopeFilter != null) {
                b.query(scopeFilter);
            }
        }).map(page -> page.<ServiceDirectoryEntry>map(record -> record));
    }

    /**
     * Returns the entries of the platform's own services, the ones with no owning organization.
     */
    public Future<Page<ServiceDirectoryEntry>> findSystemEntries(Pageable pageable) {
        return findAll(pageable, b -> b.query(q -> q.bool(bq -> bq.mustNot(n -> n.exists(e -> e.field("organizationId"))))))
                .map(page -> page.<ServiceDirectoryEntry>map(record -> record));
    }

    /**
     * Returns the online MCP tools a caller in the given scope may call, flattened from the matching entries. The
     * search {@code _source}-filters to the {@code mcpTools} field so contracts never leave Elasticsearch.
     */
    public Future<McpToolDefinitionList> findMcpToolsCallableBy(String organizationId,
                                                                String applicationId,
                                                                CursorPageable pageable) {
        // a service exposing MCP tools is callable whether or not it also is "advertised" see @Publish
        Query filter;
        try {
            filter = composeFilter(termFilter("mcpExposed", true),
                                   termFilter("online", true),
                                   zoneVisibilityFilter(organizationId, applicationId));
        } catch (IllegalArgumentException e) {
            // a scope whose id is no zone label fails the call, never the caller's thread
            return Future.failedFuture(e);
        }
        return findAll(pageable, b -> {
            b.query(filter);
            // cri is used for service invocation, must never be served in a listing, so we filter it
            b.source(sc -> sc.filter(f -> f.includes("mcpTools").excludes("mcpTools.cri")));
        }).map(page -> {
            List<McpToolDefinition> tools = new ArrayList<>();
            for (ServiceDirectoryEntry entry : page.getContent()) {
                if (entry.getMcpTools() != null) {
                    tools.addAll(entry.getMcpTools());
                }
            }
            // the cursor tracks ENTRIES, so a short entry page is the last page and returns no cursor
            String nextCursor = page instanceof CursorPage<ServiceDirectoryRecord> cursorPage
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
    public Future<McpToolDefinition> findMcpToolByName(String toolName,
                                                       String organizationId,
                                                       String applicationId) {
        Query filter;
        try {
            filter = composeFilter(termFilter("mcpTools.name", toolName),
                                   termFilter("mcpExposed", true),
                                   termFilter("online", true),
                                   zoneVisibilityFilter(organizationId, applicationId));
        } catch (IllegalArgumentException e) {
            return Future.failedFuture(e);
        }
        return findAll(Pageable.ofSize(RESOLUTION_PAGE_SIZE), b -> {
            b.query(filter);
            b.source(sc -> sc.filter(f -> f.includes("mcpTools")));
        }).compose(page -> {
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
            Future<McpToolDefinition> ret;
            if (tools.size() > 1) {
                // minted names are unique system wide, so duplicates mean this index holds corrupted data
                // needing manual repair; the detail stays in this log and the caller gets a generic failure
                log.error("MCP tool name '{}' resolved to {} services, OrgId {}, AppId {}, the service directory index is corrupted. Provided by: {}",
                          toolName,
                          tools.size(),
                          organizationId,
                          applicationId,
                          tools.stream().map(McpToolDefinition::getCri).toList());
                ret = Future.failedFuture(new IllegalStateException("MCP tool resolution failed for '" + toolName + "'"));
            } else {
                ret = Future.succeededFuture(tools.isEmpty() ? null : tools.getFirst());
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

    // The listing view of the zone send rules enforced at dispatch time by ZoneRules, narrowed to the zones this
    // server reaches: system sees all zones, an organization sees management-api + app-api + app.<org>, the zone
    // every one of its applications lives under, an application sees its own app.<org>.<app> zone + app-api. A
    // zone is listed with its sub-zones, as ZoneRules sends to them, and an id becomes a zone label only once
    // validated as one, as ZoneRules does, so an id holding a dot cannot name another scope's zone
    private Query zoneVisibilityFilter(String organizationId, String applicationId) {
        Optional<Set<String>> zones;
        if (organizationId == null) {
            zones = zonePartitioningService.reachableZones();
        } else {
            ZoneUtil.validateLabel(organizationId);
            Set<String> callerZones;
            if (applicationId == null) {
                callerZones = Set.of(DomainUtil.MANAGEMENT_API_ZONE,
                                     DomainUtil.APP_API_ZONE,
                                     DomainUtil.APP_ZONE_PREFIX + "." + organizationId);
            } else {
                ZoneUtil.validateLabel(applicationId);
                callerZones = Set.of(DomainUtil.applicationZone(organizationId, applicationId),
                                     DomainUtil.APP_API_ZONE);
            }
            zones = Optional.of(callerZones.stream().filter(zonePartitioningService::reachesZone).collect(Collectors.toSet()));
        }
        return zones.map(this::inZones).orElse(null);
    }

    private Query inZones(Set<String> zones) {
        Query ret;
        if (zones.isEmpty()) {
            // no zone left to show: match nothing, rather than leave an empty bool to the engine
            ret = Query.of(q -> q.bool(b -> b.mustNot(n -> n.matchAll(m -> m))));
        } else {
            ret = Query.of(q -> q.bool(b -> {
                for (String zone : zones) {
                    b.should(termFilter("zone", zone));
                    b.should(s -> s.prefix(p -> p.field("zone").value(zone + ".")));
                }
                return b.minimumShouldMatch("1");
            }));
        }
        return ret;
    }
}
