package org.mindignited.kinotic.telemetry;

import org.mindignited.continuum.api.annotations.EnableContinuum;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Standalone Spring Boot application for the OTel collector.
 * This can be run as a standalone service to collect and forward telemetry data.
 */
@EnableContinuum
@SpringBootApplication
@EnableScheduling
public class KinoticTelemetryApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(KinoticTelemetryApplication.class, args);
    }
}

