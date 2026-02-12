

package org.kinotic.rpc.internal.config;


import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes Continuum by setting up spring to scan internal code
 *
 *
 * Created by Navid Mitchell on 11/28/18.
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan("org.kinotic.rpc.internal")
public class ContinuumConfiguration {
}
