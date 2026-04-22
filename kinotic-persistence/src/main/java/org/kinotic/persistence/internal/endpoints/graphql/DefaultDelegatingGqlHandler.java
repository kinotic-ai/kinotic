package org.kinotic.persistence.internal.endpoints.graphql;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;
import lombok.extern.slf4j.Slf4j;

import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Created by Navíd Mitchell 🤪 on 11/19/24.
 */
@Slf4j
@Component
public class DefaultDelegatingGqlHandler implements DelegatingGqlHandler {

    private final AsyncLoadingCache<GqlCacheKey, GraphQLHandler> graphQLHandlerCache;

    public DefaultDelegatingGqlHandler(GqlSchemaHandlerCacheLoader gqlSchemaHandlerCacheLoader,
                                       DefaultCaffeineCacheFactory cacheFactory) {
        graphQLHandlerCache = cacheFactory.<GqlCacheKey, GraphQLHandler>newBuilder()
                .name("graphQLHandlerCache")
                .expireAfterAccess(Duration.ofHours(20))
                .maximumSize(2000)
                .buildAsync(gqlSchemaHandlerCacheLoader);
    }

    /**
     * Evicts the caches for an application event. This can be a change to a named
     * query or a {@link EntityDefinition}.
     * 
     * @param cacheEvictionEvent the event containing the {@link EntityDefinition} or named query to evict the
     *              caches for
     */
    @EventListener
    public void handleCacheEviction(CacheEvictionEvent cacheEvictionEvent) {
        try {
            if (cacheEvictionEvent.getApplicationId() != null && cacheEvictionEvent.getOrganizationId() != null) {
                graphQLHandlerCache.asMap().remove(
                        new GqlCacheKey(cacheEvictionEvent.getOrganizationId(), cacheEvictionEvent.getApplicationId()));
            }
        } catch (Exception e) {
            log.error("Failed to handle cache eviction (source: {})",
                      cacheEvictionEvent.getSource(), e);
        }
    }

    @Override
    public void handle(RoutingContext rc) {
        String organization = rc.pathParam(GqlVerticle.ORGANIZATION_PATH_PARAMETER);
        String application = rc.pathParam(GqlVerticle.APPLICATION_PATH_PARAMETER);

        Future.fromCompletionStage(graphQLHandlerCache.get(new GqlCacheKey(organization, application)),
                rc.vertx().getOrCreateContext())
                .map(graphQLHandler -> {
                    graphQLHandler.handle(rc);
                    return null;
                });
    }

}
