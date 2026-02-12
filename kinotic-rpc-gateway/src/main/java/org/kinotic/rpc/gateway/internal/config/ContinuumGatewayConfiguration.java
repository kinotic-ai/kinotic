

package org.kinotic.rpc.gateway.internal.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 *
 * Created by navid on 2/10/20
 */
@Configuration
@ComponentScan({"org.kinotic.continuum.gateway.internal", "org.kinotic.continuum.gateway.api.config"})
public class ContinuumGatewayConfiguration {
}
