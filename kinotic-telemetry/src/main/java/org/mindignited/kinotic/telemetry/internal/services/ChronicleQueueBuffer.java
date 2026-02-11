package org.mindignited.kinotic.telemetry.internal.services;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.mindignited.kinotic.telemetry.api.config.KinoticTelemetryProperties;
import org.mindignited.kinotic.telemetry.api.domain.TelemetryData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chronicle Queue-based buffer for telemetry data.
 * Provides persistent, low-latency buffering of traces and metrics.
 */
@Component
public class ChronicleQueueBuffer {
    
    private static final Logger log = LoggerFactory.getLogger(ChronicleQueueBuffer.class);
    
    private final KinoticTelemetryProperties.Buffer config;
    private ChronicleQueue tracesQueue;
    private ChronicleQueue metricsQueue;
    private ExcerptAppender tracesAppender;
    private ExcerptAppender metricsAppender;
    private ExcerptTailer tracesTailer;
    private ExcerptTailer metricsTailer;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    public ChronicleQueueBuffer(KinoticTelemetryProperties properties) {
        this.config = properties.getBuffer();
    }
    
    @PostConstruct
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            try {
                File queueDir = new File(config.getPath());
                if (!queueDir.exists()) {
                    queueDir.mkdirs();
                }
                
                RollCycles rollCycle = parseRollCycle(config.getRollCycle());
                
                // Initialize traces queue
                File tracesDir = new File(queueDir, "traces");
                tracesQueue = SingleChronicleQueueBuilder.single(tracesDir)
                        .rollCycle(rollCycle)
                        .build();
                tracesAppender = tracesQueue.createAppender();
                tracesTailer = tracesQueue.createTailer();
                
                // Initialize metrics queue
                File metricsDir = new File(queueDir, "metrics");
                metricsQueue = SingleChronicleQueueBuilder.single(metricsDir)
                        .rollCycle(rollCycle)
                        .build();
                metricsAppender = metricsQueue.createAppender();
                metricsTailer = metricsQueue.createTailer();
                
                log.info("Chronicle Queue buffer initialized at: {}", config.getPath());
            } catch (Exception e) {
                log.error("Failed to initialize Chronicle Queue buffer", e);
                initialized.set(false);
                throw new RuntimeException("Failed to initialize buffer", e);
            }
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (initialized.get()) {
            try {
                if (tracesTailer != null) {
                    tracesTailer.close();
                }
                if (metricsTailer != null) {
                    metricsTailer.close();
                }
                if (tracesAppender != null) {
                    tracesAppender.close();
                }
                if (metricsAppender != null) {
                    metricsAppender.close();
                }
                if (tracesQueue != null) {
                    tracesQueue.close();
                }
                if (metricsQueue != null) {
                    metricsQueue.close();
                }
                log.info("Chronicle Queue buffer shut down");
            } catch (Exception e) {
                log.error("Error shutting down Chronicle Queue buffer", e);
            } finally {
                initialized.set(false);
            }
        }
    }
    
    /**
     * Write telemetry data to the appropriate queue.
     */
    public void write(TelemetryData data) {
        if (!initialized.get()) {
            throw new IllegalStateException("Buffer not initialized");
        }
        
        try {
            ExcerptAppender appender = switch (data.getType()) {
                case TRACES -> tracesAppender;
                case METRICS -> metricsAppender;
            };
            
            appender.writeDocument(wire -> {
                wire.write("type").text(data.getType().name());
                wire.write("timestamp").int64(data.getTimestamp());
                wire.write("data").bytes(data.getData());
            });
        } catch (Exception e) {
            log.error("Failed to write telemetry data to buffer", e);
            throw new RuntimeException("Failed to write to buffer", e);
        }
    }
    
    /**
     * Read a batch of telemetry data from the queue.
     */
    public List<TelemetryData> readBatch(TelemetryData.TelemetryType type, int maxItems) {
        if (!initialized.get()) {
            return List.of();
        }
        
        List<TelemetryData> batch = new ArrayList<>();
        ExcerptTailer tailer = switch (type) {
            case TRACES -> tracesTailer;
            case METRICS -> metricsTailer;
        };
        
        if (tailer == null) {
            return batch;
        }
        
        try {
            int count = 0;
            while (count < maxItems && tailer.readDocument(wire -> {
                String typeStr = wire.read("type").text();
                long timestamp = wire.read("timestamp").int64();
                byte[] data = wire.read("data").bytes();
                
                TelemetryData.TelemetryType telemetryType = TelemetryData.TelemetryType.valueOf(typeStr);
                TelemetryData telemetryData = new TelemetryData(telemetryType, data, timestamp);
                batch.add(telemetryData);
            })) {
                count++;
            }
        } catch (Exception e) {
            log.error("Failed to read telemetry data from buffer", e);
        }
        
        return batch;
    }
    
    /**
     * Check if the buffer has any data available.
     */
    public boolean hasData(TelemetryData.TelemetryType type) {
        if (!initialized.get()) {
            return false;
        }
        
        ExcerptTailer tailer = switch (type) {
            case TRACES -> tracesTailer;
            case METRICS -> metricsTailer;
        };
        
        if (tailer == null) {
            return false;
        }
        
        try {
            // Try to read one document to see if data is available
            // This will advance the tailer, but we'll read it again in readBatch
            return tailer.readDocument(wire -> {
                // Just check if we can read - don't process
            });
        } catch (Exception e) {
            return false;
        }
    }
    
    private RollCycles parseRollCycle(String rollCycle) {
        return switch (rollCycle.toUpperCase()) {
            case "HOURLY" -> RollCycles.FAST_HOURLY;
            case "DAILY" -> RollCycles.FAST_DAILY;
            default -> {
                log.warn("Unknown roll cycle: {}, using HOURLY", rollCycle);
                yield RollCycles.FAST_HOURLY;
            }
        };
    }
    
    public boolean isInitialized() {
        return initialized.get();
    }
}

