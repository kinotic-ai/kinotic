package org.kinotic.system;

import org.kinotic.core.api.annotations.EnableKinotic;
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
@EnableKinotic // registers org.kinotic.orchestrator so @Proxy interfaces like VmManagerProxy are scanned
@ConditionalOnProperty(value = "kinotic.disableSystemApi", havingValue = "false", matchIfMissing = true)
public class KinoticSystemApiLibrary {

}
