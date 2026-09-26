package org.kinotic.grind;

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
public class KinoticGrindLibrary {

}
