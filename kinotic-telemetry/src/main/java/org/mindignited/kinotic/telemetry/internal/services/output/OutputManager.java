package org.mindignited.kinotic.telemetry.internal.services.output;

import org.mindignited.kinotic.telemetry.api.domain.TelemetryData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple output processors and routes telemetry data to them.
 */
@Component
public class OutputManager {
    
    private static final Logger log = LoggerFactory.getLogger(OutputManager.class);
    
    private final Map<String, OutputProcessor> processors = new ConcurrentHashMap<>();
    
    /**
     * Register an output processor.
     * If the manager is already initialized, the processor will be initialized immediately.
     */
    public void register(OutputProcessor processor) {
        if (processor == null) {
            throw new IllegalArgumentException("Processor cannot be null");
        }
        
        String type = processor.getType();
        if (processors.containsKey(type)) {
            log.warn("Output processor of type '{}' already registered, replacing", type);
        }
        
        processors.put(type, processor);
        log.info("Registered output processor: {}", type);
        
        // If already initialized, initialize this processor immediately
        // (This handles late registration after @PostConstruct has run)
        try {
            if (!processor.isReady()) {
                processor.initialize();
                log.info("Initialized newly registered output processor: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to initialize newly registered processor: {}", type, e);
        }
    }
    
    /**
     * Unregister an output processor.
     */
    public void unregister(String type) {
        OutputProcessor processor = processors.remove(type);
        if (processor != null) {
            processor.shutdown();
            log.info("Unregistered output processor: {}", type);
        }
    }
    
    /**
     * Get an output processor by type.
     */
    public OutputProcessor getProcessor(String type) {
        return processors.get(type);
    }
    
    /**
     * Process telemetry data through all enabled processors.
     */
    public CompletableFuture<Void> process(List<TelemetryData> data) {
        if (data == null || data.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        List<OutputProcessor> enabledProcessors = processors.values().stream()
                .filter(OutputProcessor::isEnabled)
                .filter(OutputProcessor::isReady)
                .toList();
        
        if (enabledProcessors.isEmpty()) {
            log.debug("No enabled output processors available");
            return CompletableFuture.completedFuture(null);
        }
        
        List<CompletableFuture<Void>> futures = enabledProcessors.stream()
                .map(processor -> {
                    try {
                        return processor.process(data);
                    } catch (Exception e) {
                        log.error("Error processing data with processor: {}", processor.getType(), e);
                        return CompletableFuture.<Void>failedFuture(e);
                    }
                })
                .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Get all registered processor types.
     */
    public List<String> getProcessorTypes() {
        return List.copyOf(processors.keySet());
    }
    
    /**
     * Initialize all registered processors.
     * This is called after all beans are created and processors are registered.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing output processors...");
        processors.values().forEach(processor -> {
            try {
                // Only initialize if not already initialized
                if (!processor.isReady()) {
                    processor.initialize();
                    log.info("Initialized output processor: {}", processor.getType());
                }
            } catch (Exception e) {
                log.error("Failed to initialize output processor: {}", processor.getType(), e);
            }
        });
    }
    
    /**
     * Shutdown all registered processors.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down output processors...");
        processors.values().forEach(processor -> {
            try {
                processor.shutdown();
            } catch (Exception e) {
                log.error("Error shutting down processor: {}", processor.getType(), e);
            }
        });
        processors.clear();
    }
}

