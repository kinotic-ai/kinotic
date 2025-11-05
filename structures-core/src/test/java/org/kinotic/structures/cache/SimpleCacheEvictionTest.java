package org.kinotic.structures.cache;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import org.junit.jupiter.api.Test;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.structures.ElasticsearchTestBase;
import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.structures.api.services.EntitiesService;
import org.kinotic.structures.internal.api.services.EntityService;
import org.kinotic.structures.internal.api.services.impl.DefaultEntitiesService;
import org.kinotic.structures.internal.cache.events.CacheEvictionEvent;
import org.kinotic.structures.internal.cache.events.CacheEvictionSource;
import org.kinotic.structures.internal.cache.events.EvictionSourceOperation;
import org.kinotic.structures.internal.cache.events.EvictionSourceType;
import org.kinotic.structures.internal.api.domain.DefaultEntityContext;
import org.kinotic.structures.support.StructureAndPersonHolder;
import org.kinotic.structures.internal.sample.DummyParticipant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import reactor.test.StepVerifier;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for cache eviction functionality using real services
 * Follows the project pattern of using TestContainers and real Spring context
 */
@SpringBootTest
class SimpleCacheEvictionTest extends ElasticsearchTestBase {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private EntitiesService entitiesService;

    private StructureAndPersonHolder createAndVerifyBulk(int numberOfPeopleToCreate,
                                                         boolean randomPeople,
                                                         EntityContext entityContext,
                                                         String structureSuffix) {
        StructureAndPersonHolder ret = new StructureAndPersonHolder();

        StepVerifier.create(testHelper.createPersonStructureAndEntitiesBulk(numberOfPeopleToCreate,
                randomPeople,
                entityContext,
                structureSuffix))
                .expectNextMatches(structureAndPersonHolder -> {
                    boolean matches = structureAndPersonHolder.getStructure() != null &&
                            structureAndPersonHolder.getStructure().getId() != null &&
                            structureAndPersonHolder.getPersons().size() == numberOfPeopleToCreate;
                    if (matches) {
                        ret.setStructure(structureAndPersonHolder.getStructure());
                        ret.setPersons(structureAndPersonHolder.getPersons());
                    }
                    return matches;
                })
                .verifyComplete();
        return ret;
    }

    @Test
    void testStructureCacheEvictionWithRealServices() throws Exception {
        int numberOfPeopleToCreate = 50;
        EntityContext context = new DefaultEntityContext(new DummyParticipant("tenant1", "user1"));

        StructureAndPersonHolder holder = createAndVerifyBulk(numberOfPeopleToCreate, true, context, "_testEviction");

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

        // WHEN: Cache eviction event is published
        CacheEvictionEvent evictionEvent = CacheEvictionEvent.localModifiedStructure(
                holder.getStructure().getApplicationId(), 
                holder.getStructure().getId());
        assertDoesNotThrow(() -> eventPublisher.publishEvent(evictionEvent));

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

        StructureAndPersonHolder holder = createAndVerifyBulk(numberOfPeopleToCreate, true, context, "_testEvictionEvent");
        
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
        
        // WHEN: Structure cache eviction event is published
        CacheEvictionEvent event = CacheEvictionEvent.localModifiedStructure(
                holder.getStructure().getApplicationId(), 
                holder.getStructure().getId());

        // Then: Event publishing and handling should not throw exceptions
        assertDoesNotThrow(() -> eventPublisher.publishEvent(event));

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

        StructureAndPersonHolder holder = createAndVerifyBulk(numberOfPeopleToCreate, true, context, "_testNamedQueryEviction");
        
        // WHEN: NamedQuery cache eviction event is published
        CacheEvictionEvent event = CacheEvictionEvent.localModifiedNamedQuery(
                holder.getStructure().getApplicationId(), 
                holder.getStructure().getId(), 
                "testQueryId");

        // Then: Event publishing and handling should not throw exceptions
        assertDoesNotThrow(() -> eventPublisher.publishEvent(event));

        // Note: NamedQuery cache eviction is harder to test without creating actual named queries
        // This test primarily verifies the event handling doesn't throw exceptions
    }

    @Test
    void testNullValidation() {
        // Test that null events don't cause issues
        assertDoesNotThrow(() -> eventPublisher.publishEvent(null));
        
        // Test that events with null fields are handled gracefully
        CacheEvictionEvent nullEvent = new CacheEvictionEvent(
                CacheEvictionSource.LOCAL_MESSAGE, 
                EvictionSourceType.STRUCTURE, 
                EvictionSourceOperation.MODIFY, 
                null, null, null);
        assertDoesNotThrow(() -> eventPublisher.publishEvent(nullEvent));
    }

}
