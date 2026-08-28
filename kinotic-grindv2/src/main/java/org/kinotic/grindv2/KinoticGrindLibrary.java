package org.kinotic.grindv2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Library entry point for the kinotic-grind module.
 * Enables component scanning of all module packages and configuration properties binding.
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
@ConditionalOnProperty(value = "kinotic.disableGrind", havingValue = "false", matchIfMissing = true)
public class KinoticGrindLibrary {

}
