package org.kinotic.domain.internal.api;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.directory.ServiceDirectoryStrategy;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ServiceDirectoryStrategy} persisting directory entries in Elasticsearch.
 */
@Component
@RequiredArgsConstructor
public class ElasticServiceDirectoryStrategy implements ServiceDirectoryStrategy {

    private final ServiceDirectoryEntryRepository repository;

    @Override
    public CompletableFuture<ServiceDirectoryEntry> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public CompletableFuture<Void> upsertContract(ServiceDirectoryEntry entry) {
        return repository.upsertContract(entry);
    }

    @Override
    public CompletableFuture<Void> setOnline(String entryId, boolean online, Instant when) {
        return repository.setOnline(entryId, online, when);
    }

    @Override
    public CompletableFuture<Void> setOnlineByAddress(String serviceAddress, boolean online, Instant when) {
        return repository.setOnlineByAddress(serviceAddress, online, when);
    }

    @Override
    public CompletableFuture<Void> reconcileLiveness(Set<String> activeAddresses, Instant when) {
        return repository.reconcileLiveness(activeAddresses, when);
    }

    @Override
    public CompletableFuture<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                              String applicationId,
                                                                              Pageable pageable) {
        return repository.findEntriesScopedTo(organizationId, applicationId, pageable);
    }

    @Override
    public CompletableFuture<Page<McpToolDefinition>> findMcpToolsCallableBy(String organizationId,
                                                                             String applicationId,
                                                                             Pageable pageable) {
        return repository.findMcpToolsCallableBy(organizationId, applicationId, pageable);
    }

}
