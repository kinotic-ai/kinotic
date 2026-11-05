package org.kinotic.domain.internal.api;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.McpToolDefinitionList;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.directory.ServiceDirectoryStrategy;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ServiceDirectoryStrategy} persisting directory entries in Elasticsearch. A written contract renews the
 * intent of the authorization store the entry belongs to, or of every store for a system contract, so the store's
 * model is generated again from the directory.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticServiceDirectoryStrategy implements ServiceDirectoryStrategy {

    private final ServiceDirectoryEntryRepository repository;
    private final AuthzStoreRepository authzStores;

    @Override
    public Future<Set<String>> upsertEntry(ServiceDirectoryEntry entry) {
        return upsertEntries(List.of(entry));
    }

    @Override
    public Future<Set<String>> upsertEntries(Collection<ServiceDirectoryEntry> entries) {
        return repository.upsertEntries(entries).onSuccess(changed -> renewStoresOf(entries, changed));
    }

    // The entries are stored whether or not the store's renewal lands; a store left behind is regenerated when
    // the reconcile master next starts, which reconciles every store. Every application's model includes system
    // services, so a changed system contract renews every store
    private void renewStoresOf(Collection<ServiceDirectoryEntry> entries, Set<String> changed) {
        Set<String> stores = new HashSet<>();
        boolean systemChanged = false;
        for (ServiceDirectoryEntry entry : entries) {
            if (changed.contains(entry.getId())) {
                if (entry.getApplicationId() == null) {
                    systemChanged = true;
                } else {
                    stores.add(AuthzGraphUtil.authzApplicationId(entry.getOrganizationId(), entry.getApplicationId()));
                }
            }
        }
        if (systemChanged) {
            authzStores.renewAllDesired("service directory")
                       .onFailure(e -> log.error("Could not renew the authorization stores after the system's contracts changed", e));
        } else {
            for (String store : stores) {
                authzStores.renewDesired(store, "service directory")
                           .onFailure(e -> log.error("Could not renew the authorization store {} after its contracts changed", store, e));
            }
        }
    }

    @Override
    public Future<Void> setOnline(String entryId, boolean online, Instant when) {
        return repository.setOnline(entryId, online, when);
    }

    @Override
    public Future<Void> setOnline(Map<String, Boolean> onlineByEntryId, Instant when) {
        return repository.setOnline(onlineByEntryId, when);
    }

    @Override
    public Future<Void> setOnlineByAddress(String serviceAddress, boolean online, Instant when) {
        return repository.setOnlineByAddress(serviceAddress, online, when);
    }

    @Override
    public Future<Void> reconcileLiveness(Set<String> activeAddresses, Instant when) {
        return repository.reconcileLiveness(activeAddresses, when);
    }

    @Override
    public Future<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                   String applicationId,
                                                                   Pageable pageable) {
        return repository.findEntriesScopedTo(organizationId, applicationId, pageable);
    }

    @Override
    public Future<Page<ServiceDirectoryEntry>> findSystemEntries(Pageable pageable) {
        return repository.findSystemEntries(pageable);
    }

    @Override
    public Future<ServiceDirectoryEntry> findEntry(String entryId) {
        return repository.findById(entryId).map(ServiceDirectoryEntry.class::cast);
    }

    @Override
    public Future<McpToolDefinition> findMcpToolByName(String toolName,
                                                       String organizationId,
                                                       String applicationId) {
        return repository.findMcpToolByName(toolName, organizationId, applicationId);
    }

    @Override
    public Future<McpToolDefinitionList> findMcpToolsCallableBy(String organizationId,
                                                                String applicationId,
                                                                CursorPageable pageable) {
        return repository.findMcpToolsCallableBy(organizationId, applicationId, pageable);
    }

}
