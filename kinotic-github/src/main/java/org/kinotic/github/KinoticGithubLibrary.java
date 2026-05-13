package org.kinotic.github;

import org.kinotic.github.api.config.KinoticGithubProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ComponentScan
@ConditionalOnProperty(value = "kinotic.disableGithub", havingValue = "false", matchIfMissing = true)
public class KinoticGithubLibrary {
}
