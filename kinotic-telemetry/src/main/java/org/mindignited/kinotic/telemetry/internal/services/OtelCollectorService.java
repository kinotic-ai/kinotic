package org.mindignited.kinotic.telemetry.internal.services;

import org.mindignited.kinotic.telemetry.api.config.KinoticTelemetryProperties;
import org.mindignited.kinotic.telemetry.api.domain.TelemetryData;
import org.mindignited.kinotic.telemetry.internal.services.output.OutputManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main service that orchestrates the OTel collector components.
 * Manages the flow: Receiver → Buffer → Output
 */
@Service
public class OtelCollectorService {
    
    private static final Logger log = LoggerFactory.getLogger(OtelCollectorService.class);
    
    private final ChronicleQueueBuffer buffer;
    private final OutputManager outputManager;
    private final KinoticTelemetryProperties.Buffer bufferConfig;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public OtelCollectorService(
            ChronicleQueueBuffer buffer,
            OutputManager outputManager,
            KinoticTelemetryProperties properties) {
        this.buffer = buffer;
        this.outputManager = outputManager;
        this.bufferConfig = properties.getBuffer();
    }
    
    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting OTel collector service...");
            
            // Start the scheduler for reading from buffer and sending to outputs
            scheduler = Executors.newScheduledThreadPool(2);
            
            // Schedule periodic reading from buffer for traces
            scheduler.scheduleWithFixedDelay(
                    () -> processBuffer(TelemetryData.TelemetryType.TRACES),
                    0,
                    bufferConfig.getFlushIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
            
            // Schedule periodic reading from buffer for metrics
            scheduler.scheduleWithFixedDelay(
                    () -> processBuffer(TelemetryData.TelemetryType.METRICS),
                    0,
                    bufferConfig.getFlushIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
            
            log.info("OTel collector service started");
        }
    }
    
    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping OTel collector service...");
            
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.warn("Scheduler did not terminate gracefully, forcing shutdown");
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for scheduler to terminate");
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            log.info("OTel collector service stopped");
        }
    }
    
    /**
     * Process data from the buffer and send to outputs.
     */
    private void processBuffer(TelemetryData.TelemetryType type) {
        if (!buffer.isInitialized()) {
            return;
        }
        
        try {
            // Check if there's data available
            if (!buffer.hasData(type)) {
                return;
            }
            
            // Read a batch from the buffer
            List<TelemetryData> batch = buffer.readBatch(type, bufferConfig.getBatchSize());
            
            if (batch.isEmpty()) {
                return;
            }
            
            log.debug("Processing batch of {} {} items", batch.size(), type);
            
            // Send to output processors
            outputManager.process(batch)
                    .thenRun(() -> log.debug("Successfully processed batch of {} {} items", batch.size(), type))
                    .exceptionally(error -> {
                        log.error("Error processing batch of {} {} items", batch.size(), type, error);
                        return null;
                    });
            
        } catch (Exception e) {
            log.error("Error processing buffer for type: {}", type, e);
        }
    }
    
    public boolean isRunning() {
        return running.get();
    }
}

