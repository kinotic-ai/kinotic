package org.kinotic.domain.internal.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.ClusterEventPublisher;
import org.kinotic.domain.api.cache.CacheEvictionEvent;
import org.kinotic.domain.api.cache.CacheEvictionSource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Carries a cache eviction raised on this node to every node of the cluster. A service that changes an entity
 * definition or a named query raises a local {@link CacheEvictionEvent}; every node, this one included, then receives
 * its cluster form and evicts the caches the event names.
 *
 * Created By Nic Padilla on 2/12/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterCacheEvictionService {

    private final ClusterEventPublisher clusterEventPublisher;

    @EventListener
    public void handleCacheEviction(CacheEvictionEvent event) {
        // the cluster form is what the nodes receive, so it is never broadcast again
        if (event.getEvictionSource() == CacheEvictionSource.LOCAL_MESSAGE) {
            clusterEventPublisher.publish(event.clusterMessage())
                                 .onFailure(error -> log.error("The {} cache eviction of {}:{} for {} was not delivered to every node",
                                                               event.getEvictionSourceType(), event.getEntityDefinitionId(),
                                                               event.getNamedQueryId(), event.getApplicationKey(), error));
        }
    }

}
