package org.kinotic.stream.internal.api.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.stream.api.model.StreamDefinition;
import org.kinotic.stream.api.model.StreamPosition;
import org.kinotic.stream.api.services.StreamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots a Kinotic node with this module's auto-configuration and checks {@code kinotic.stream.dataDirectory}
 * decides where the {@link StreamService} it provides stores its streams.
 */
@SpringBootTest
public class StreamServiceWiringTests {

    @TempDir
    private static Path dataDirectory;

    @Autowired
    private StreamService streamService;

    @DynamicPropertySource
    static void streamProperties(DynamicPropertyRegistry registry) {
        registry.add("kinotic.stream.dataDirectory", dataDirectory::toString);
    }

    @Test
    public void streamsAreStoredInTheConfiguredDataDirectory() throws Exception {
        streamService.createStreamIfNotExist(new StreamDefinition("wired", 2))
                     .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        StreamPosition position = streamService.append("wired", "key", new byte[]{1})
                                               .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals(0, position.offset());
        assertTrue(Files.isDirectory(dataDirectory.resolve("wired").resolve("shards").resolve(String.valueOf(position.shard()))));
    }
}
