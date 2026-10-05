package org.kinotic.app;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Library entry point for the kinotic-app-api module: the services an application's users and runtimes call,
 * published in the application zones the app server hosts.
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
@EnableKinotic
public class KinoticAppApiLibrary {
}
