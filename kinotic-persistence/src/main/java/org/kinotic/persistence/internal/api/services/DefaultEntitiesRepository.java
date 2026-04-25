package org.kinotic.persistence.internal.api.services;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.TenantSpecificId;
import org.kinotic.persistence.api.services.EntitiesRepository;
import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.kinotic.persistence.internal.cache.events.EvictionSourceType;
import org.kinotic.persistence.api.model.ParameterHolder;
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
public class DefaultEntitiesRepository implements EntitiesRepository {

    private final AsyncLoadingCache<String, EntityRepository> entityServiceCache;

    public DefaultEntitiesRepository(EntityServiceCacheLoader entityServiceCacheLoader,
                                     DefaultCaffeineCacheFactory cacheFactory) {
        this.entityServiceCache
                = cacheFactory.<String, EntityRepository>newBuilder()
                          .name("entityServiceCache")
                          .expireAfterAccess(Duration.ofHours(20))
                          .maximumSize(2000)
                          .buildAsync(entityServiceCacheLoader);
    }

    /**
     * Evicts the caches for a given {@link EntityDefinition}, this is used when a {@link EntityDefinition} is updated on a remote node.
     * @param event the event containing the {@link EntityDefinition} to evict the caches for
     */
    @EventListener
    public void handleEntityDefinitionCacheEviction(CacheEvictionEvent event) {

        try {
                
            if(event.getEvictionSourceType() == EvictionSourceType.ENTITY_DEFINITION){
                this.entityServiceCache.asMap().remove(event.getEntityDefinitionId());
            }
                    
        } catch (Exception e) {
            log.error("failed to handle EntityDefinition cache eviction (source: {})",
                     event.getEvictionSource().getDisplayName(), e);
        }
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Void> bulkSave(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                                T entities,
                                                EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.bulkSave(entities, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Void> bulkUpdate(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                                  T entities,
                                                  EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.bulkUpdate(entities, context));
    }

    @WithSpan
    @Override
    public CompletableFuture<Long> count(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                         EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.count(context));
    }

    @WithSpan
    @Override
    public CompletableFuture<Long> countByQuery(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                                String query,
                                                EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.countByQuery(query, context));
    }

    @WithSpan
    @Override
    public CompletableFuture<Void> deleteById(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                              String id,
                                              EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.deleteById(id, context));
    }

    @Override
    public CompletableFuture<Void> deleteById(String entityDefinitionId, TenantSpecificId id, EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.deleteById(id, context));
    }

    @WithSpan
    @Override
    public CompletableFuture<Void> deleteByQuery(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                                 String query,
                                                 EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.deleteByQuery(query, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Page<T>> findAll(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                                  Pageable pageable,
                                                  Class<T> type,
                                                  EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.findAll(pageable, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<T> findById(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                             String id,
                                             Class<T> type,
                                             EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.findById(id, type, context));
    }

    @Override
    public <T> CompletableFuture<T> findById(String entityDefinitionId, TenantSpecificId id, Class<T> type, EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.findById(id, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<List<T>> findByIds(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                                    List<String> ids,
                                                    Class<T> type,
                                                    EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.findByIds(ids, type, context));
    }

    @Override
    public <T> CompletableFuture<List<T>> findByIdsWithTenant(String entityDefinitionId,
                                                              List<TenantSpecificId> ids,
                                                              Class<T> type,
                                                              EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.findByIdsWithTenant(ids, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<List<T>> namedQuery(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                                     @SpanAttribute("queryName") String queryName,
                                                     ParameterHolder parameterHolder,
                                                     Class<T> type,
                                                     EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.namedQuery(queryName, parameterHolder, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Page<T>> namedQueryPage(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                                         @SpanAttribute("queryName") String queryName,
                                                         ParameterHolder parameterHolder,
                                                         Pageable pageable,
                                                         Class<T> type,
                                                         EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.namedQueryPage(queryName,
                                                                                 parameterHolder,
                                                                                 pageable,
                                                                                 type,
                                                                                 context));
    }

    @Override
    public CompletableFuture<Void> syncIndex(String entityDefinitionId,
                                             EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.syncIndex(context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<T> save(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                         T entity,
                                         EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.save(entity, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<Page<T>> search(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                                 String searchText,
                                                 Pageable pageable,
                                                 Class<T> type,
                                                 EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.search(searchText, pageable, type, context));
    }

    @WithSpan
    @Override
    public <T> CompletableFuture<T> update(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                           T entity,
                                           EntityContext context) {
        return entityServiceCache.get(entityDefinitionId)
                .thenCompose(entityRepository -> entityRepository.update(entity, context));
    }

}
