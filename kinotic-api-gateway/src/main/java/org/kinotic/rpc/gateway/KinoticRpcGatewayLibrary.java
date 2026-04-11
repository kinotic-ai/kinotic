package org.kinotic.rpc.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * This class provides the necessary configuration annotations to enable this library for use in Spring boot applications
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
@ConditionalOnProperty(value = "kinotic.disableRpcGateway", havingValue = "false", matchIfMissing = true)
public class KinoticRpcGatewayLibrary {
}
