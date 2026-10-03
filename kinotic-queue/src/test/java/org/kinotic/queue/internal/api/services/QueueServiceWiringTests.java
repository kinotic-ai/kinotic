package org.kinotic.queue.internal.api.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.model.QueueDefinition;
import org.kinotic.queue.api.model.QueuePosition;
import org.kinotic.queue.api.services.QueueService;
import org.kinotic.queue_test.QueueTestApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kinotic.queue.internal.QueueTestSupport.await;

/**
 * Boots a Kinotic node with this module's auto-configuration: the node carries the queue node attribute, so shards
 * are placed on it, and {@code kinotic.queue.dataDirectory} decides where they are stored.
 */
@SpringBootTest(classes = QueueTestApplication.class)
public class QueueServiceWiringTests {

    @TempDir
    private static Path dataDirectory;

    @Autowired
    private QueueService queueService;

    @DynamicPropertySource
    static void queueProperties(DynamicPropertyRegistry registry) {
        registry.add("kinotic.queue.dataDirectory", dataDirectory::toString);
        registry.add("kinotic.queue.replicationFactor", () -> 1);
    }

    @Test
    public void queuesAreStoredOnThisNodeInTheConfiguredDataDirectory() throws Exception {
        await(queueService.createQueueIfNotExist(new QueueDefinition("wired", 2)));

        QueuePosition position = await(queueService.append("wired", "key", new byte[]{1}));

        assertEquals(0, position.offset());
        assertTrue(Files.isDirectory(dataDirectory.resolve("wired").resolve("shards").resolve(String.valueOf(position.shard()))));
    }
}
