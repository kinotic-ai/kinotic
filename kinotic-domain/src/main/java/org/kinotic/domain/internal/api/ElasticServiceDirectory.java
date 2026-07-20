package org.kinotic.domain.internal.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.AbstractServiceDirectory;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.kinotic.idl.api.converter.IdlConverterFactory;
import org.kinotic.idl.api.directory.SchemaFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The Elasticsearch-backed {@link ServiceDirectory}. Its presence as a bean is what activates the registration
 * path's directory calls. Contract upserts leave the liveness fields alone; the liveness owner is the single writer
 * for those.
 */
@Slf4j
@Component
public class ElasticServiceDirectory extends AbstractServiceDirectory {

    private final ServiceDirectoryEntryRepository repository;
    private final EventBusService eventBusService;

    // Collapses repeated NO_HANDLERS reports for the same CRI into one verification (seconds).
    private final Cache<String, Boolean> reportDebounce = Caffeine.newBuilder()
                                                                  .expireAfterWrite(Duration.ofSeconds(5))
                                                                  .build();

    public ElasticServiceDirectory(ServiceDirectoryEntryRepository repository,
                                   EventBusService eventBusService,
                                   SchemaFactory schemaFactory,
                                   IdlConverterFactory idlConverterFactory) {
        super(schemaFactory, idlConverterFactory);
        this.repository = repository;
        this.eventBusService = eventBusService;
    }

    @Override
    protected CompletableFuture<Void> registerService(ServiceIdentifier serviceIdentifier,
                                                         Class<?> serviceInterface) {
        // buildEntry reflects the interface and generates schemas — skipped when the stored entry
        // was already built by this kinotic version
        return repository.findById(entryId(serviceIdentifier))
                         .thenCompose(existing -> {
                             if (existing != null && Objects.equals(existing.getSourceVersion(), getSourceVersion())) {
                                 return CompletableFuture.completedFuture(null);
                             }
                             // a new entry starts with online unset, and the ACTIVE registration event fired
                             // before the entry existed — refresh from the verified cluster state
                             return repository.upsertContract(buildEntry(serviceIdentifier, serviceInterface))
                                              .thenCompose(v -> refreshOnline(serviceIdentifier));
                         });
    }

    @Override
    protected CompletableFuture<Void> unregisterService(ServiceIdentifier serviceIdentifier) {
        // this node leaving says nothing about other instances of the service — verify, never
        // write offline blindly
        return refreshOnline(serviceIdentifier);
    }

    /**
     * Sets the entry's liveness to the verified cluster-wide registration state.
     */
    private CompletableFuture<Void> refreshOnline(ServiceIdentifier serviceIdentifier) {
        return eventBusService.isAnybodyListening(serviceIdentifier.cri())
                              .toCompletionStage()
                              .toCompletableFuture()
                              .thenCompose(online -> repository.setOnline(entryId(serviceIdentifier),
                                                                          online,
                                                                          Instant.now()));
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
        return eventBusService.isAnybodyListening(parsed)
                              .toCompletionStage()
                              .toCompletableFuture()
                              .thenCompose(online -> repository.setOnlineByAddress(parsed.baseResource(),
                                                                                  online,
                                                                                  Instant.now()));
    }

    @Override
    public CompletableFuture<Void> updateLiveness(String serviceAddress, boolean online) {
        return repository.setOnlineByAddress(serviceAddress, online, Instant.now());
    }

    @Override
    public CompletableFuture<Void> reconcileLiveness(Set<String> activeServiceAddresses) {
        return repository.reconcileLiveness(activeServiceAddresses, Instant.now());
    }

}
