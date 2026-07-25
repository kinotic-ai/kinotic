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
import io.vertx.core.Future;

/**
 * {@link ServiceDirectoryStrategy} persisting directory entries in Elasticsearch.
 */
@Component
@RequiredArgsConstructor
public class ElasticServiceDirectoryStrategy implements ServiceDirectoryStrategy {

    private final ServiceDirectoryEntryRepository repository;

    @Override
    public Future<Void> upsertEntry(ServiceDirectoryEntry entry) {
        return Future.fromCompletionStage(repository.upsertEntry(entry));
    }

    @Override
    public Future<Void> setOnline(String entryId, boolean online, Instant when) {
        return Future.fromCompletionStage(repository.setOnline(entryId, online, when));
    }

    @Override
    public Future<Void> setOnlineByAddress(String serviceAddress, boolean online, Instant when) {
        return Future.fromCompletionStage(repository.setOnlineByAddress(serviceAddress, online, when));
    }

    @Override
    public Future<Void> reconcileLiveness(Set<String> activeAddresses, Instant when) {
        return Future.fromCompletionStage(repository.reconcileLiveness(activeAddresses, when));
    }

    @Override
    public Future<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                              String applicationId,
                                                                              Pageable pageable) {
        return Future.fromCompletionStage(repository.findEntriesScopedTo(organizationId, applicationId, pageable));
    }

    @Override
    public Future<McpToolDefinition> findMcpToolByName(String toolName,
                                                                  String organizationId,
                                                                  String applicationId) {
        return Future.fromCompletionStage(repository.findMcpToolByName(toolName, organizationId, applicationId));
    }

    @Override
    public Future<Page<McpToolDefinition>> findMcpToolsCallableBy(String organizationId,
                                                                             String applicationId,
                                                                             Pageable pageable) {
        return Future.fromCompletionStage(repository.findMcpToolsCallableBy(organizationId, applicationId, pageable));
    }

}
