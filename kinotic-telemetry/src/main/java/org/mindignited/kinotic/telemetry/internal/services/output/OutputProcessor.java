package org.mindignited.kinotic.telemetry.internal.services.output;

import org.mindignited.kinotic.telemetry.api.domain.TelemetryData;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for output processors that send telemetry data to backends.
 */
public interface OutputProcessor {
    
    /**
     * Get the type identifier for this output processor.
     */
    String getType();
    
    /**
     * Initialize the output processor.
     */
    void initialize() throws Exception;
    
    /**
     * Shutdown the output processor.
     */
    void shutdown();
    
    /**
     * Process a batch of telemetry data.
     * 
     * @param data List of telemetry data to process
     * @return CompletableFuture that completes when the data has been sent
     */
    CompletableFuture<Void> process(List<TelemetryData> data);
    
    /**
     * Check if the output processor is enabled.
     */
    boolean isEnabled();
    
    /**
     * Check if the output processor is ready to process data.
     */
    boolean isReady();
}

