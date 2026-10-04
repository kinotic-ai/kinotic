package org.kinotic.queue.internal.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.queue.api.config.QueueProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how consumer positions are stored, merged and expired, against real files, with a retention period of one
 * second.
 */
public class ConsumerOffsetRepositoryTests {

    private static final QueueProperties ONE_SECOND = new QueueProperties().setRetentionPeriod(Duration.ofSeconds(1));

    @TempDir
    private Path directory;

    @Test
    public void aPositionUnusedForTheRetentionPeriodExpiresUnlessSavedAgain() throws Exception {
        try (ConsumerOffsetRepository positions = new ConsumerOffsetRepository(directory, 2, ONE_SECOND)) {
            positions.save("billing", 0, 10);
            positions.save("audit", 1, 5);
            Thread.sleep(600);
            // Saving the stored offset again only marks it used
            positions.save("billing", 0, 10);
            Thread.sleep(600);

            assertEquals(10, positions.findNextOffset("billing", 0));
            assertEquals(0, positions.findNextOffset("audit", 1));
            assertEquals(10, positions.findAll(0).get("billing").nextOffset());
            assertTrue(positions.findAll(1).isEmpty());

            // An expired position starts over: a lower offset is stored again
            positions.save("audit", 1, 2);
            assertEquals(2, positions.findNextOffset("audit", 1));
        }
    }

    @Test
    public void positionsFromAnotherCopyKeepTheLaterOffsetAndUseAndExpiredOnesAreIgnored() {
        try (ConsumerOffsetRepository positions = new ConsumerOffsetRepository(directory, 1, ONE_SECOND)) {
            long now = System.currentTimeMillis();
            positions.save("billing", 0, 10);

            positions.saveAll(0, Map.of("billing", new ConsumerPosition(7, now + 500),
                                        "audit", new ConsumerPosition(3, now - 5_000)));

            ConsumerPosition billing = positions.findAll(0).get("billing");
            assertEquals(10, billing.nextOffset());
            assertEquals(now + 500, billing.lastUsedMillis());
            assertEquals(0, positions.findNextOffset("audit", 0));
        }
    }

    @Test
    public void expiredPositionsAreDeletedWithTheFilesOfConsumersLeftWithNone() throws Exception {
        try (ConsumerOffsetRepository positions = new ConsumerOffsetRepository(directory, 2, ONE_SECOND)) {
            positions.save("billing", 0, 10);
            positions.save("audit", 0, 5);
            Thread.sleep(1_200);
            positions.save("billing", 1, 4);

            positions.deleteExpired();

            assertFalse(Files.exists(directory.resolve("audit")));
            assertTrue(Files.exists(directory.resolve("billing")));
            assertEquals(0, positions.findNextOffset("billing", 0));
            assertEquals(4, positions.findNextOffset("billing", 1));
        }
    }

    @Test
    public void aPositionKeepsWhenItWasLastUsedAcrossAReopen() throws Exception {
        try (ConsumerOffsetRepository positions = new ConsumerOffsetRepository(directory, 1, ONE_SECOND)) {
            positions.save("billing", 0, 10);
        }
        try (ConsumerOffsetRepository reopened = new ConsumerOffsetRepository(directory, 1, ONE_SECOND)) {
            assertEquals(10, reopened.findNextOffset("billing", 0));
            Thread.sleep(1_200);
            assertEquals(0, reopened.findNextOffset("billing", 0));
        }
    }
}
