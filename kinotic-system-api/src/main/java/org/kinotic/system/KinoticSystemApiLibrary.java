package org.kinotic.system;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Library entry point for the kinotic-system-api module.
 * Enables component scanning of all module packages and configuration properties binding.
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
@EnableKinotic // registers org.kinotic.system so @Proxy interfaces like VmManagerProxy are scanned
@ConditionalOnProperty(value = "kinotic.disableSystemApi", havingValue = "false", matchIfMissing = true)
public class KinoticSystemApiLibrary {

}
