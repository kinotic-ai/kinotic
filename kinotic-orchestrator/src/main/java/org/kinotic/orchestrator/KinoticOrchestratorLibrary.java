package org.kinotic.orchestrator;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Library entry point for the kinotic-orchestrator module.
 * Enables component scanning of all orchestrator packages and configuration properties binding.
 */
@Configuration
@ComponentScan
@EnableConfigurationProperties
@EnableKinotic // enables proxy scanning and registration
public class KinoticOrchestratorLibrary {

}
