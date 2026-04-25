package org.kinotic.persistence.internal.api.services;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.ParameterHolder;
import org.kinotic.persistence.api.services.NamedQueriesDefinitionService;
import org.kinotic.persistence.api.services.NamedQueriesService;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;
import org.kinotic.persistence.internal.api.services.sql.QueryExecutorFactory;
import org.kinotic.persistence.internal.api.services.sql.executors.QueryExecutor;
import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.kinotic.persistence.internal.cache.events.EvictionSourceType;
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
public class DefaultNamedQueriesService implements NamedQueriesService {

    private final AsyncLoadingCache<CacheKey, QueryExecutor> cache;
    private final ConcurrentHashMap<String, List<CacheKey>> cacheKeyTracker = new ConcurrentHashMap<>();

    public DefaultNamedQueriesService(DefaultCaffeineCacheFactory cacheFactory,
                                      NamedQueriesDefinitionService namedQueriesDefinitionService,
                                      QueryExecutorFactory queryExecutorFactory,
                                      SecurityContext securityContext) {

        cache = cacheFactory.<CacheKey, QueryExecutor>newBuilder()
                            .name("namedQueriesCache")
                            .expireAfterAccess(Duration.ofHours(20))
                            .maximumSize(10_000)
                            .buildAsync((key, executor) -> securityContext.withElevatedAccess(() ->
                                    namedQueriesDefinitionService
                                    .findByApplicationAndEntityDefinition(key.entityDefinition().getApplicationId(),
                                                                          key.entityDefinition().getName()))
                                    .thenApplyAsync(namedQueriesDefinition -> {

                                        Validate.notNull(namedQueriesDefinition, "No Named Query found for EntityDefinition: "
                                                + key.entityDefinition()
                                                + " and Query: "
                                                + key.queryName());

                                        QueryExecutor ret = queryExecutorFactory.createQueryExecutor(key.entityDefinition(),
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
    public <T> CompletableFuture<List<T>> executeNamedQuery(EntityDefinition entityDefinition,
                                                            String queryName,
                                                            ParameterHolder parameterHolder,
                                                            Class<T> type,
                                                            EntityContext context) {
        // Authorization happens in the QueryExecutor so we don't need an additional cache to hold the NamedQueryAuthorizationService
        return cache.get(new CacheKey(queryName, entityDefinition))
                    .thenCompose(queryExecutor -> queryExecutor.execute(new QueryContext(context, parameterHolder), type));
    }

    @Override
    public <T> CompletableFuture<Page<T>> executeNamedQueryPage(EntityDefinition entityDefinition,
                                                                String queryName,
                                                                ParameterHolder parameterHolder,
                                                                Pageable pageable,
                                                                Class<T> type,
                                                                EntityContext context) {
        // Authorization happens in the QueryExecutor so we don't need an additional cache to hold the NamedQueryAuthorizationService
        return cache.get(new CacheKey(queryName, entityDefinition))
                    .thenCompose(queryExecutor -> queryExecutor.executePage(new QueryContext(context, parameterHolder), pageable, type));
    }

    private record CacheKey(String queryName, EntityDefinition entityDefinition) {}
}
