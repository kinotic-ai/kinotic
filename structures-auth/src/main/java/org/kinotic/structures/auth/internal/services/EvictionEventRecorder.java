package org.kinotic.structures.auth.internal.services;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.structures.auth.api.domain.EvictionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * Records cache eviction events to a CSV file asynchronously.
 * Only active when the "eviction-tracking" Spring profile is enabled.
 * <p>
 * Created by Nic Padilla on 1/4/26.
 */
@Slf4j
@Component
@Profile("eviction-tracking")
public class EvictionEventRecorder {

    private final Path outputPath;
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    public EvictionEventRecorder(
        @Value("${structures.cache.eviction.csv.path:./eviction-events.csv}") String path) {
        this.outputPath = Path.of(path);
        log.info("Eviction event recorder initialized, writing to: {}", outputPath.toAbsolutePath());
    }

    /**
     * Records an eviction event asynchronously to the CSV file.
     *
     * @param event the eviction event to record
     */
    public void record(EvictionEvent event) {
        log.info("Recording eviction event: {}", event);
        writeExecutor.execute(() -> {
            try {
                Files.writeString(outputPath, event.toCsvLine(), CREATE, APPEND);
            } catch (IOException e) {
                log.error("Failed to write eviction event to CSV: {}", event, e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

