package org.kinotic.test.tests.core;

import org.apache.ignite.Ignite;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.ClusterEventPublisher;
import org.kinotic.domain.api.cache.CacheEvictionEvent;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that this node, which runs the persistence module, carries the attribute a cache eviction names, as the
 * module advertises it, and that the eviction reaches it through the publisher.
 */
@SpringBootTest
public class ClusterEventTests extends KinoticTestBase {

    @Autowired
    private Ignite ignite;

    @Autowired
    private ClusterEventPublisher clusterEventPublisher;

    @Test
    public void aCacheEvictionNamesAnAttributeThePersistenceModuleAdvertises() throws Exception {
        CacheEvictionEvent event = CacheEvictionEvent.entityDefinition(new ApplicationKey(TEST_ORG_ID, TEST_APP_ID),
                                                                       "entity-" + suffix());

        // the local node's attributes are the ones its Ignite configuration advertised to the cluster
        Map<String, Object> attributes = ignite.cluster().localNode().attributes();
        for (String name : event.nodeAttributeFilter()) {
            assertTrue(attributes.containsKey(name), "this node carries " + name + " named by the eviction");
        }
        await(clusterEventPublisher.publish(event));
    }

}
