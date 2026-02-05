package org.kinotic.structures.internal.api.services.impl;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;

import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.structures.api.domain.TenantSpecificId;
import org.kinotic.structures.api.services.EntitiesService;
import org.kinotic.structures.auth.internal.services.DefaultCaffeineCacheFactory;
import org.kinotic.structures.internal.api.services.EntityService;
import org.kinotic.structures.internal.cache.events.CacheEvictionEvent;
import org.kinotic.structures.internal.cache.events.EvictionSourceType;
import org.kinotic.structures.api.domain.ParameterHolder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪on 5/10/23.
 */
@Slf4j
@Component
public class DefaultEntitiesService implements EntitiesService {

    private final AsyncLoadingCache<String, EntityService> entityServiceCache;

    public DefaultEntitiesService(EntityServiceCacheLoader entityServiceCacheLoader,
                                  DefaultCaffeineCacheFactory cacheFactory) {
        this.entityServiceCache
                = cacheFactory.<String, EntityService>newBuilder()
                          .name("entityServiceCache")
                          .expireAfterAccess(Duration.ofHours(20))
                          .maximumSize(2000)
                          .buildAsync(entityServiceCacheLoader);
    }

    /**
     * Evicts the caches for a given structure, this is used when a structure is updated on a remote node.
     * @param event the event containing the structure to evict the caches for
     */
    @EventListener
    public void handleStructureCacheEviction(CacheEvictionEvent event) {

        try {
                
            if(event.getEvictionSourceType() == EvictionSourceType.STRUCTURE){
                this.entityServiceCache.asMap().remove(event.getStructureId());

                log.info("successfully completed cache eviction for structure: {} due to {}", 
                                 event.getStructureId(), event.getEvictionOperation().getDisplayName());
            }
                    
        } catch (Exception e) {
            log.error("failed to handle structure cache eviction (source: {})", 
                     event.getEvictionSource().getDisplayName(), e);
        }
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Void> bulkSave(@SpanAttribute("structureId") String structureId,
                                                T entities,
                                                EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.bulkSave(entities, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Void> bulkUpdate(@SpanAttribute("structureId") String structureId,
                                                  T entities,
                                                  EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.bulkUpdate(entities, context));
    }

    @WithSpan
    @Override
    public CompletableFuture<Long> count(@SpanAttribute("structureId") String structureId,
                                         EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.count(context));
    }

    @WithSpan
    @Override
    public CompletableFuture<Long> countByQuery(@SpanAttribute("structureId") String structureId,
                                                String query,
                                                EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.countByQuery(query, context));
    }

    @WithSpan
    @Override
    public CompletableFuture<Void> deleteById(@SpanAttribute("structureId") String structureId,
                                              String id,
                                              EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.deleteById(id, context));
    }

    @Override
    public CompletableFuture<Void> deleteById(String structureId, TenantSpecificId id, EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.deleteById(id, context));
    }

    @WithSpan
    @Override
    public CompletableFuture<Void> deleteByQuery(@SpanAttribute("structureId") String structureId,
                                                 String query,
                                                 EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.deleteByQuery(query, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Page<T>> findAll(@SpanAttribute("structureId") String structureId,
                                                  Pageable pageable,
                                                  Class<T> type,
                                                  EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.findAll(pageable, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<T> findById(@SpanAttribute("structureId") String structureId,
                                             String id,
                                             Class<T> type,
                                             EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.findById(id, type, context));
    }

    @Override
    public <T> CompletableFuture<T> findById(String structureId, TenantSpecificId id, Class<T> type, EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.findById(id, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<List<T>> findByIds(@SpanAttribute("structureId") String structureId,
                                                    List<String> ids,
                                                    Class<T> type,
                                                    EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.findByIds(ids, type, context));
    }

    @Override
    public <T> CompletableFuture<List<T>> findByIdsWithTenant(String structureId,
                                                              List<TenantSpecificId> ids,
                                                              Class<T> type,
                                                              EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.findByIdsWithTenant(ids, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<List<T>> namedQuery(@SpanAttribute("structureId") String structureId,
                                                     @SpanAttribute("queryName") String queryName,
                                                     ParameterHolder parameterHolder,
                                                     Class<T> type,
                                                     EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.namedQuery(queryName, parameterHolder, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Page<T>> namedQueryPage(@SpanAttribute("structureId") String structureId,
                                                         @SpanAttribute("queryName") String queryName,
                                                         ParameterHolder parameterHolder,
                                                         Pageable pageable,
                                                         Class<T> type,
                                                         EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.namedQueryPage(queryName,
                                                                           parameterHolder,
                                                                           pageable,
                                                                           type,
                                                                           context));
    }

    @Override
    public CompletableFuture<Void> syncIndex(String structureId,
                                             EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.syncIndex(context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<T> save(@SpanAttribute("structureId") String structureId,
                                         T entity,
                                         EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.save(entity, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Page<T>> search(@SpanAttribute("structureId") String structureId,
                                                 String searchText,
                                                 Pageable pageable,
                                                 Class<T> type,
                                                 EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.search(searchText, pageable, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<T> update(@SpanAttribute("structureId") String structureId,
                                           T entity,
                                           EntityContext context) {
        return entityServiceCache.get(structureId)
                .thenCompose(entityService -> entityService.update(entity, context));
    }

    /**
     * Public accessor for testing cache state
     */
    public AsyncLoadingCache<String, EntityService> getEntityServiceCache() {
        return entityServiceCache;
    }
}
