package org.kinotic.persistence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * This class provides the necessary configuration annotations to enable this library for use in Spring boot applications
 */
@Configuration
@ComponentScan
@EnableConfigurationProperties
public class KinoticPersistenceLibrary {
}
