package org.mindignited.kinotic.telemetry.api.config;

import org.mindignited.kinotic.telemetry.internal.services.output.OutputManager;
import org.mindignited.kinotic.telemetry.internal.services.output.otlp.OtlpGrpcOutput;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for the telemetry collector.
 * Registers output processors and enables scheduling.
 */
@Configuration
@EnableScheduling
public class TelemetryConfiguration {
    
    private final OutputManager outputManager;
    private final OtlpGrpcOutput otlpGrpcOutput;
    
    public TelemetryConfiguration(OutputManager outputManager, OtlpGrpcOutput otlpGrpcOutput) {
        this.outputManager = outputManager;
        this.otlpGrpcOutput = otlpGrpcOutput;
    }
    
    /**
     * Register the OTLP gRPC output processor with the output manager.
     * This happens after all beans are created, ensuring proper initialization order.
     */
    @PostConstruct
    public void registerOutputProcessors() {
        outputManager.register(otlpGrpcOutput);
        // The OutputManager's @PostConstruct will initialize all registered processors
    }
}

