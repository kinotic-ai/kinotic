package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import lombok.extern.slf4j.Slf4j;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.services.crud.Page;
import org.kinotic.core.api.services.crud.Pageable;
import org.kinotic.core.internal.api.services.AbstractCrudService;
import org.kinotic.core.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.domain.EntityContext;
import org.kinotic.persistence.api.domain.NamedQueriesDefinition;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.services.NamedQueriesService;
import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.api.domain.ParameterHolder;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;
import org.kinotic.persistence.internal.api.services.sql.QueryExecutorFactory;
import org.kinotic.persistence.internal.api.services.sql.executors.QueryExecutor;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.kinotic.persistence.internal.cache.events.EvictionSourceType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Navíd Mitchell 🤪 on 4/23/24.
 */
@Slf4j
@Component
public class DefaultNamedQueriesService extends AbstractCrudService<NamedQueriesDefinition> implements NamedQueriesService {

    private final AsyncLoadingCache<CacheKey, QueryExecutor> cache;
    private final ConcurrentHashMap<String, List<CacheKey>> cacheKeyTracker = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public DefaultNamedQueriesService(CrudServiceTemplate crudServiceTemplate,
                                      ElasticsearchAsyncClient esAsyncClient,
                                      QueryExecutorFactory queryExecutorFactory,
                                      ApplicationEventPublisher eventPublisher,
                                      DefaultCaffeineCacheFactory cacheFactory) {
        super("struct_named_query_service_definition",
              NamedQueriesDefinition.class,
              esAsyncClient,
              crudServiceTemplate);

        this.eventPublisher = eventPublisher;

        cache = cacheFactory.<CacheKey, QueryExecutor>newBuilder()
                        .name("namedQueriesCache")
                        .expireAfterAccess(Duration.ofHours(20))
                        .maximumSize(10_000) 
                        .buildAsync((key, executor) -> findByApplicationAndStructure(key.structure().getApplicationId(),
                                                                                     key.structure().getName())
                                .thenApplyAsync(namedQueriesDefinition -> {
 
                                    Validate.notNull(namedQueriesDefinition, "No Named Query found for Structure: "
                                            + key.structure()
                                            + " and Query: "
                                            + key.queryName());

                                    QueryExecutor ret = queryExecutorFactory.createQueryExecutor(key.structure(),
                                                                                                 key.queryName(),
                                                                                                 namedQueriesDefinition);

                                    // Track the cache key so, we can invalidate it when the named query is updated
                                    cacheKeyTracker.compute(namedQueriesDefinition.getId(), (s, cacheKeys) -> {
                                        if(cacheKeys == null){
                                            cacheKeys = new ArrayList<>();
                                        }
                                        cacheKeys.add(key);
                                        return cacheKeys;
                                    });
                                    return ret;
                                }, executor));

    }


    /**
     * Evicts the caches for a given named query, this is used when a named query is updated on a remote node.
     * @param event the event containing the named query to evict the caches for
     */
    @EventListener
    public void handleNamedQueryCacheEviction(CacheEvictionEvent event) {
        
        try {

            if(event.getEvictionSourceType() == EvictionSourceType.NAMED_QUERY){
                cacheKeyTracker.computeIfPresent(event.getNamedQueryId(), (s, cacheKeys) -> {
                    for (CacheKey cacheKey : cacheKeys) {
                        cache.synchronous().invalidate(cacheKey);
                    }
                    return null;
                });
                        
                log.info("successfully completed cache eviction for named query: {} due to {}", 
                                event.getNamedQueryId(), event.getEvictionSource().getDisplayName());
            }

        } catch (Exception e) {
            log.error("failed to handle named query cache eviction (source: {})", 
                     event.getEvictionSource().getDisplayName(), e);
        }
    }

    @Override
    public <T> CompletableFuture<List<T>> executeNamedQuery(Structure structure,
                                                            String queryName,
                                                            ParameterHolder parameterHolder,
                                                            Class<T> type,
                                                            EntityContext context) {
        // Authorization happens in the QueryExecutor so we don't need an additional cache to hold the NamedQueryAuthorizationService
        return cache.get(new CacheKey(queryName, structure))
                    .thenCompose(queryExecutor -> queryExecutor.execute(new QueryContext(context, parameterHolder), type));
    }

    @Override
    public <T> CompletableFuture<Page<T>> executeNamedQueryPage(Structure structure,
                                                                String queryName,
                                                                ParameterHolder parameterHolder,
                                                                Pageable pageable,
                                                                Class<T> type,
                                                                EntityContext context) {
        // Authorization happens in the QueryExecutor so we don't need an additional cache to hold the NamedQueryAuthorizationService
        return cache.get(new CacheKey(queryName, structure))
                    .thenCompose(queryExecutor -> queryExecutor.executePage(new QueryContext(context, parameterHolder), pageable, type));
    }

    @Override
    public CompletableFuture<NamedQueriesDefinition> findByApplicationAndStructure(String applicationId, String structure) {
        return crudServiceTemplate.search(indexName, Pageable.ofSize(1), type, builder -> builder
                .query(q -> q
                        .bool(b -> b
                                .filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery(),
                                        TermQuery.of(tq -> tq.field("structure").value(structure))._toQuery())
                        )
                )).thenApply(page -> page.getContent() != null && !page.getContent().isEmpty()
                ? page.getContent().getFirst()
                : null);
    }

    @Override
    public CompletableFuture<NamedQueriesDefinition> save(NamedQueriesDefinition entity) {
        // TODO: preprocess queries to correct index name and add Metadata about query type to be used by other parts of the system
        //       The Query type information will speed up other areas the need this as well
        return super.save(entity)
                    .thenApply(namedQueriesDefinition -> {
                        this.eventPublisher.publishEvent(CacheEvictionEvent.localModifiedNamedQuery(entity.getApplicationId(), entity.getStructure(), entity.getId()));
                        return namedQueriesDefinition;
                    });
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return findById(id)
                .thenCompose(namedQuery -> {
                    if (namedQuery == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("NamedQuery cannot be found for id: " + id));
                    }
                    
                    return super.deleteById(id)
                            .thenApply(v -> {
                                this.eventPublisher.publishEvent(
                                        CacheEvictionEvent.localDeletedNamedQuery(
                                                namedQuery.getApplicationId(), 
                                                namedQuery.getStructure(), 
                                                namedQuery.getId()));
                                return null;
                            });
                });
    }

    @Override
    public CompletableFuture<Page<NamedQueriesDefinition>> search(String searchText, Pageable pageable) {
        return crudServiceTemplate.search(indexName,
                                          pageable,
                                          NamedQueriesDefinition.class,
                                          builder -> builder.q(searchText));
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
                            .refresh(b -> b.index(indexName))
                            .thenApply(unused -> null);
    }

    private record CacheKey(String queryName, Structure structure) {}
}
