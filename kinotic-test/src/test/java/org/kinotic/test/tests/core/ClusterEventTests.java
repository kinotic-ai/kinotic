package org.kinotic.test.tests.core;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.ClusterEventPublisher;
import org.kinotic.domain.api.cache.CacheEvictionEvent;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Pins that a cache eviction reaches every node through the publisher, this one included.
 */
@SpringBootTest
public class ClusterEventTests extends KinoticTestBase {

    @Autowired
    private ClusterEventPublisher clusterEventPublisher;

    @Test
    public void aCacheEvictionIsHandledByEveryNode() throws Exception {
        CacheEvictionEvent event = CacheEvictionEvent.entityDefinition(new ApplicationKey(TEST_ORG_ID, TEST_APP_ID),
                                                                       "entity-" + suffix());

        await(clusterEventPublisher.publish(event));
    }

}
