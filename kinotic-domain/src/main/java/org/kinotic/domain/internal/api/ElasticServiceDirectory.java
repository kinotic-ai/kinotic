package org.kinotic.domain.internal.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.McpToolC3Decorator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * The Elasticsearch-backed {@link ServiceDirectory}. Its presence as a bean is what activates core's capture path.
 * <p>
 * Enforces the write-path scope invariant and recomputes {@code mcpExposed} from the contract so the flag is never
 * trusted from input. Contract upserts leave the liveness fields alone; the liveness owner is the single writer for
 * those.
 */
@Slf4j
@Component
public class ElasticServiceDirectory implements ServiceDirectory {

    private final ServiceDirectoryEntryRepository repository;
    private final EventBusService eventBusService;

    // Collapses repeated NO_HANDLERS reports for the same CRI into one verification (seconds).
    private final Cache<String, Boolean> reportDebounce = Caffeine.newBuilder()
                                                                  .expireAfterWrite(Duration.ofSeconds(5))
                                                                  .build();

    public ElasticServiceDirectory(ServiceDirectoryEntryRepository repository,
                                   EventBusService eventBusService) {
        this.repository = repository;
        this.eventBusService = eventBusService;
    }

    @Override
    public CompletableFuture<Void> register(ServiceDirectoryEntry entry) {
        if (entry.getApplicationId() != null && entry.getOrganizationId() == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("applicationId requires organizationId for entry " + entry.getId()));
        }
        entry.setMcpExposed(computeMcpExposed(entry));
        return repository.upsertContract(entry);
    }

    @Override
    public CompletableFuture<Void> unregister(String entryId) {
        return repository.setOnline(entryId, false, Instant.now());
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

    @Override
    public CompletableFuture<Void> reportUnreachable(String cri) {
        if (reportDebounce.getIfPresent(cri) != null) {
            return CompletableFuture.completedFuture(null);
        }
        reportDebounce.put(cri, Boolean.TRUE);
        CRI parsed = CRI.create(cri);
        // verify against current registrations before writing; a report is an invalidation trigger, not a value
        return eventBusService.hasListeners(parsed)
                              .toCompletionStage()
                              .toCompletableFuture()
                              .thenCompose(online -> repository.setOnlineByAddress(parsed.baseResource(),
                                                                                  online,
                                                                                  Instant.now()));
    }

    private boolean computeMcpExposed(ServiceDirectoryEntry entry) {
        ServiceDefinition contract = entry.getContract();
        if (contract == null || contract.getFunctions() == null) {
            return false;
        }
        for (FunctionDefinition function : contract.getFunctions()) {
            if (function.containsDecorator(McpToolC3Decorator.class)) {
                return true;
            }
        }
        return false;
    }

}
