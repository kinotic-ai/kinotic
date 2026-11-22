package org.kinotic.domain.internal.api;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.McpToolDefinitionList;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.directory.ServiceDirectoryStrategy;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ServiceDirectoryStrategy} persisting directory entries in Elasticsearch. Contracts are written as
 * {@link AuthzModelService#writeContracts} writes them, so a registration no model can be generated from fails with
 * the reason.
 */
@Component
@RequiredArgsConstructor
public class ElasticServiceDirectoryStrategy implements ServiceDirectoryStrategy {

    private final ServiceDirectoryEntryRepository repository;
    // the model service reads the directory this strategy backs, so it is resolved once both exist
    private final ObjectProvider<AuthzModelService> models;

    @Override
    public Future<Set<String>> upsertEntry(ServiceDirectoryEntry entry) {
        return upsertEntries(List.of(entry));
    }

    @Override
    public Future<Set<String>> upsertEntries(Collection<ServiceDirectoryEntry> entries) {
        return models.getObject().writeContracts(entries, () -> repository.upsertEntries(entries));
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
