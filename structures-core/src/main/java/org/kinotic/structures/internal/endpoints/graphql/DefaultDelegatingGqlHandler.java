package org.kinotic.structures.internal.endpoints.graphql;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;
import lombok.extern.slf4j.Slf4j;

import org.kinotic.structures.internal.cache.events.CacheEvictionEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Created by Navíd Mitchell 🤪 on 11/19/24.
 */
@Slf4j
@Component
public class DefaultDelegatingGqlHandler implements DelegatingGqlHandler {

    private final AsyncLoadingCache<String, GraphQLHandler> graphQLHandlerCache;

    public DefaultDelegatingGqlHandler(GqlSchemaHandlerCacheLoader gqlSchemaHandlerCacheLoader) {
        graphQLHandlerCache
                = Caffeine.newBuilder()
                          .expireAfterAccess(20, TimeUnit.HOURS)
                          .maximumSize(2000)
                          .buildAsync(gqlSchemaHandlerCacheLoader);
    }


    /**
     * Evicts the caches for a application event.  This can be an change to a named query or a structure.
     * @param event the event containing the structure or named query to evict the caches for
     */
    @EventListener
    public void handleCacheEviction(ApplicationEvent event) {
        log.debug("handling cache eviction (source: {})", 
                 event.getSource());

        try {
            // we need to clear on both eviction types 
            if(event instanceof CacheEvictionEvent cacheEvictionEvent){

                if(cacheEvictionEvent.getApplicationId() != null){
                    graphQLHandlerCache.asMap().remove(cacheEvictionEvent.getApplicationId());   

                    log.info("Successfully completed cache eviction for entity: {}:{}:{} due to {} {} {}", 
                                     cacheEvictionEvent.getApplicationId(), cacheEvictionEvent.getStructureId(), cacheEvictionEvent.getNamedQueryId(), 
                                     cacheEvictionEvent.getEvictionSourceType(), cacheEvictionEvent.getEvictionOperation(), cacheEvictionEvent.getEvictionSource().getDisplayName());
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
