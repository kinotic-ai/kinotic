package org.kinotic.management;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * This class provides the necessary configuration annotations to enable this library for use in Spring boot applications
 */
@Configuration
@EnableConfigurationProperties
// The github packages are gated separately by GithubConfiguration (kinotic.disableGithub),
// so this scan must not register them
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                                                      pattern = "org\\.kinotic\\.os\\..*\\.github\\..*"))
@ConditionalOnProperty(value = "kinotic.disableOsApi", havingValue = "false", matchIfMissing = true)
public class KinoticManagementApiLibrary {
}
