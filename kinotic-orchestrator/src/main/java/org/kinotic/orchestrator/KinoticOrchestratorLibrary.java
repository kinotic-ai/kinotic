package org.kinotic.orchestrator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Library entry point for the kinotic-orchestrator module.
 * Enables component scanning of all orchestrator packages and configuration properties binding.
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
@ConditionalOnProperty(value = "kinotic.disableOrchestrator", havingValue = "false", matchIfMissing = true)
public class KinoticOrchestratorLibrary {

}
