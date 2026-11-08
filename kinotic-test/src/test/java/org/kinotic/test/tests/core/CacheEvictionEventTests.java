package org.kinotic.test.tests.core;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventService;
import org.kinotic.domain.api.cache.CacheEvictionEvent;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that this node, which runs the persistence module, is a confirmed consumer of cache evictions, and that
 * publishing an eviction completes once it has handled it.
 */
@SpringBootTest
public class CacheEvictionEventTests extends KinoticTestBase {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventBusService eventBusService;

    @Test
    public void thePersistenceModuleConsumesCacheEvictions() throws Exception {
        CRI confirmed = CRI.create(EventConstants.TOPIC_DESTINATION_SCHEME, "confirmed", CacheEvictionEvent.class.getName());
        assertTrue(await(eventBusService.isAnybodyListening(confirmed)), "this node is a confirmed consumer of cache evictions");

        await(eventService.publish(CacheEvictionEvent.entityDefinition(new ApplicationKey(TEST_ORG_ID, TEST_APP_ID),
                                                                       "entity-" + suffix())));
    }

}
