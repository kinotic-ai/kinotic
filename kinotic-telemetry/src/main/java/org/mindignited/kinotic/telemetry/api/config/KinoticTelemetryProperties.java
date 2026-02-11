package org.mindignited.kinotic.telemetry.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the OTel collector.
 * Created By Navíd Mitchell 🤪on 9/16/25
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "kinotic.telemetry")
public class KinoticTelemetryProperties {
    
    /**
     * Receiver configuration
     */
    private Receiver receiver = new Receiver();
    
    /**
     * Buffer configuration
     */
    private Buffer buffer = new Buffer();
    
    /**
     * Output configuration
     */
    private Output output = new Output();
    
    @Getter
    @Setter
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class Receiver {
        /**
         * Host to bind the receiver to
         */
        private String host = "0.0.0.0";
        
        /**
         * Port for the OTLP gRPC receiver (default: 4317 for gRPC)
         */
        private int port = 4317;
        
        /**
         * Enable/disable the receiver
         */
        private boolean enabled = true;
    }
    
    @Getter
    @Setter
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class Buffer {
        /**
         * Path where Chronicle Queue files will be stored
         */
        private String path = "./data/telemetry-queue";
        
        /**
         * Roll cycle for Chronicle Queue (e.g., HOURLY, DAILY)
         */
        private String rollCycle = "HOURLY";
        
        /**
         * Maximum number of items to batch before flushing
         */
        private int batchSize = 100;
        
        /**
         * Maximum time in milliseconds to wait before flushing a batch
         */
        private long flushIntervalMs = 1000;
    }
    
    @Getter
    @Setter
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class Output {
        /**
         * Output type (e.g., "otlp-grpc")
         */
        private String type = "otlp-grpc";
        
        /**
         * OTLP gRPC endpoint URL
         */
        private String endpoint = "http://localhost:4317";
        
        /**
         * Maximum number of retry attempts
         */
        private int maxRetries = 3;
        
        /**
         * Retry delay in milliseconds
         */
        private long retryDelayMs = 1000;
        
        /**
         * Enable/disable the output
         */
        private boolean enabled = true;
        
        /**
         * Timeout in milliseconds for output operations
         */
        private long timeoutMs = 5000;
    }
}
