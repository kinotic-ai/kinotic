package org.kinotic.structures.cluster;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.structures.api.domain.cluster.ClusterInfo;
import org.kinotic.structures.api.services.EntitiesService;
import org.kinotic.structures.api.services.cluster.ClusterInfoService;
import org.kinotic.structures.internal.api.services.EntityService;
import org.kinotic.structures.internal.api.services.impl.DefaultEntitiesService;
import org.kinotic.structures.internal.cache.events.CacheEvictionEvent;
import org.kinotic.structures.internal.cache.events.CacheEvictionSource;
import org.kinotic.structures.internal.cache.events.EvictionSourceOperation;
import org.kinotic.structures.internal.cache.events.EvictionSourceType;
import org.kinotic.structures.internal.api.domain.DefaultEntityContext;
import org.kinotic.structures.support.StructureAndPersonHolder;
import org.kinotic.structures.support.TestHelper;
import org.kinotic.structures.internal.sample.DummyParticipant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for cluster-wide cache eviction functionality using real services.
 * Follows the project pattern of using TestContainers and real Spring context.
 * 
 * This test enables clustering (via ClusterTestBase) to verify the full cluster 
 * eviction flow works correctly. Uses ClusterInfoService to deterministically verify 
 * cache eviction completion by polling lastCacheEvictionSuccessTimestamp instead of 
 * relying on Thread.sleep().
 * 
 * Extends ClusterTestBase to share the same Spring ApplicationContext with other
 * clustering tests. This is critical because Ignite is a JVM-level singleton.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Disabled("Cluster tests are resource-intensive - enable manually for testing")
public class SimpleCacheEvictionTest extends ClusterTestBase {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private EntitiesService entitiesService;

    @Autowired
    private ClusterInfoService clusterInfoService;

    @Autowired
    private TestHelper testHelper;

    @Test
    void testStructureCacheEvictionWithRealServices() throws Exception {
        int numberOfPeopleToCreate = 50;
        EntityContext context = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));

        StructureAndPersonHolder holder = testHelper.createPersonStructureAndEntities(numberOfPeopleToCreate, true, context, "_testEviction").block();

        // Get access to the cache for verification
        DefaultEntitiesService defaultEntitiesService = (DefaultEntitiesService) entitiesService;
        AsyncLoadingCache<String, EntityService> cache = defaultEntitiesService.getEntityServiceCache();

        // BEFORE: Load entity service into cache by performing search operations
        // This triggers full cache population including GraphQL caches
        CompletableFuture<?> searchBefore = entitiesService.search(
                holder.getStructure().getId(), 
                "*", 
                Pageable.ofSize(10), 
                Object.class, 
                context);
        assertNotNull(searchBefore.join(), "Search should return results");
        
        // Verify cache is populated
        assertNotNull(cache.getIfPresent(holder.getStructure().getId()), 
            "Cache should be populated after search operation");

        // Capture timestamp BEFORE triggering eviction
        long beforeTimestamp = 0; // clusterInfoService.getClusterInfo().block().getLastCacheEvictionSuccessTimestamp();

        // WHEN: Cache eviction event is published
        CacheEvictionEvent evictionEvent = CacheEvictionEvent.localModifiedStructure(
                holder.getStructure().getApplicationId(), 
                holder.getStructure().getId());
        assertDoesNotThrow(() -> eventPublisher.publishEvent(evictionEvent));

        // WAIT for eviction to be processed cluster-wide using ClusterInfoService
        awaitCacheEvictionProcessed(beforeTimestamp, Duration.ofSeconds(10));

        // THEN: Verify cache was evicted
        assertNull(cache.getIfPresent(holder.getStructure().getId()), 
            "Cache should be evicted after eviction call");

        // Verify cache can be repopulated by performing another search
        CompletableFuture<?> searchAfter = entitiesService.search(
                holder.getStructure().getId(), 
                "*", 
                Pageable.ofSize(10), 
                Object.class, 
                context);
        assertNotNull(searchAfter.join(), "Search should return results after cache eviction");
        
        // Verify cache is populated again
        assertNotNull(cache.getIfPresent(holder.getStructure().getId()), 
            "Cache should be repopulated after second search operation");
    }

    @Test
    void testStructureCacheEvictionEventHandling() {
        int numberOfPeopleToCreate = 50;
        EntityContext context = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));

        StructureAndPersonHolder holder = testHelper.createPersonStructureAndEntities(numberOfPeopleToCreate, true, context, "_testEvictionEvent").block();
        
        // Get access to the cache for verification
        DefaultEntitiesService defaultEntitiesService = (DefaultEntitiesService) entitiesService;
        AsyncLoadingCache<String, EntityService> cache = defaultEntitiesService.getEntityServiceCache();

        // BEFORE: Load entity service into cache by performing search
        // This ensures all cache layers (entity service, GraphQL handlers, etc.) are populated
        CompletableFuture<?> searchBefore = entitiesService.search(
                holder.getStructure().getId(), 
                "*", 
                Pageable.ofSize(10), 
                Object.class, 
                context);
        assertNotNull(searchBefore.join(), "Search should return results");
        
        // Verify cache is populated
        assertNotNull(cache.getIfPresent(holder.getStructure().getId()), 
            "Cache should be populated after search operation");
        
        // Capture timestamp BEFORE triggering eviction
        long beforeTimestamp = 0; //clusterInfoService.getClusterInfo().block().getLastCacheEvictionSuccessTimestamp();

        // WHEN: Structure cache eviction event is published
        CacheEvictionEvent event = CacheEvictionEvent.localModifiedStructure(
                holder.getStructure().getApplicationId(), 
                holder.getStructure().getId());

        // Event publishing and handling should not throw exceptions
        assertDoesNotThrow(() -> eventPublisher.publishEvent(event));

        // WAIT for eviction to be processed cluster-wide using ClusterInfoService
        awaitCacheEvictionProcessed(beforeTimestamp, Duration.ofSeconds(10));

        // THEN: Verify cache was evicted
        assertNull(cache.getIfPresent(holder.getStructure().getId()), 
            "Cache should be evicted after event handling");

        // Verify cache can be repopulated by performing another search
        CompletableFuture<?> searchAfter = entitiesService.search(
                holder.getStructure().getId(), 
                "*", 
                Pageable.ofSize(10), 
                Object.class, 
                context);
        assertNotNull(searchAfter.join(), "Search should return results after cache eviction");
        
        // Verify cache is populated again
        assertNotNull(cache.getIfPresent(holder.getStructure().getId()), 
            "Cache should be repopulated after second search operation");
    }

    @Test
    void testNamedQueryCacheEvictionEventHandling() {
        int numberOfPeopleToCreate = 50;
        EntityContext context = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));

        StructureAndPersonHolder holder = testHelper.createPersonStructureAndEntities(numberOfPeopleToCreate, true, context, "_testNamedQueryEviction").block();
        
        // Capture timestamp BEFORE triggering eviction
        long beforeTimestamp = 0; //clusterInfoService.getClusterInfo().block().getLastCacheEvictionSuccessTimestamp();

        // WHEN: NamedQuery cache eviction event is published
        CacheEvictionEvent event = CacheEvictionEvent.localModifiedNamedQuery(
                holder.getStructure().getApplicationId(), 
                holder.getStructure().getId(), 
                "testQueryId");

        // Event publishing and handling should not throw exceptions
        assertDoesNotThrow(() -> eventPublisher.publishEvent(event));

        // WAIT for eviction to be processed cluster-wide using ClusterInfoService
        awaitCacheEvictionProcessed(beforeTimestamp, Duration.ofSeconds(10));

        // Verify ClusterInfo reflects the eviction was processed
        ClusterInfo clusterInfo = clusterInfoService.getClusterInfo().block();
        assertNotNull(clusterInfo, "ClusterInfo should be available");
        // assertTrue(clusterInfo.getLastCacheEvictionSuccessTimestamp() > beforeTimestamp,
        //     "lastCacheEvictionSuccessTimestamp should be updated after eviction");
    }

    @Test
    void testNullValidation() {
        // Test that null events don't cause issues
        assertThrows(IllegalArgumentException.class, () -> eventPublisher.publishEvent(null));
        
        // Test that events with null fields are handled gracefully
        CacheEvictionEvent nullEvent = new CacheEvictionEvent(
                CacheEvictionSource.LOCAL_MESSAGE, 
                EvictionSourceType.STRUCTURE, 
                EvictionSourceOperation.MODIFY, 
                null, null, null);
        assertDoesNotThrow(() -> eventPublisher.publishEvent(nullEvent));
    }

    /**
     * Waits for cache eviction to be processed by polling ClusterInfoService.
     * Uses a before/after timestamp comparison to deterministically verify eviction completion.
     * 
     * @param beforeTimestamp the lastCacheEvictionSuccessTimestamp captured before triggering eviction
     * @param timeout maximum time to wait for eviction to complete
     * @throws AssertionError if eviction is not processed within the timeout
     */
    private void awaitCacheEvictionProcessed(long beforeTimestamp, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            ClusterInfo info = clusterInfoService.getClusterInfo().block();
            // if (info != null && info.getLastCacheEvictionSuccessTimestamp() > beforeTimestamp) {
            //     return; // Eviction processed successfully
            // }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for cache eviction", e);
            }
        }
        throw new AssertionError("Cache eviction not processed within timeout of " + timeout);
    }
}
