package org.mindignited.structures.internal.endpoints.graphql;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;
import lombok.extern.slf4j.Slf4j;

import org.mindignited.structures.auth.internal.services.DefaultCaffeineCacheFactory;
import org.mindignited.structures.internal.cache.events.CacheEvictionEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Created by Navíd Mitchell 🤪 on 11/19/24.
 */
@Slf4j
@Component
public class DefaultDelegatingGqlHandler implements DelegatingGqlHandler {

    private final AsyncLoadingCache<String, GraphQLHandler> graphQLHandlerCache;

    public DefaultDelegatingGqlHandler(GqlSchemaHandlerCacheLoader gqlSchemaHandlerCacheLoader,
                                       DefaultCaffeineCacheFactory cacheFactory) {
        graphQLHandlerCache = cacheFactory.<String, GraphQLHandler>newBuilder()
                .name("graphQLHandlerCache")
                .expireAfterAccess(Duration.ofHours(20))
                .maximumSize(2000)
                .buildAsync(gqlSchemaHandlerCacheLoader);
    }

    /**
     * Evicts the caches for a application event. This can be an change to a named
     * query or a structure.
     * 
     * @param event the event containing the structure or named query to evict the
     *              caches for
     */
    @EventListener
    public void handleCacheEviction(CacheEvictionEvent event) {

        try {

                if (cacheEvictionEvent.getApplicationId() != null) {
                    graphQLHandlerCache.asMap().remove(cacheEvictionEvent.getApplicationId());
                }
            }

        } catch (Exception e) {
            log.error("Failed to handle cache eviction (source: {})",
                    event.getSource(), e);
        }
    }

    @Override
    public void handle(RoutingContext rc) {
        String application = rc.pathParam(GqlVerticle.APPLICATION_PATH_PARAMETER);

        Future.fromCompletionStage(graphQLHandlerCache.get(application),
                rc.vertx().getOrCreateContext())
                .map(graphQLHandler -> {
                    graphQLHandler.handle(rc);
                    return null;
                });
    }

}
