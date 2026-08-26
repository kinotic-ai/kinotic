package org.kinotic.os.internal.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the GitHub integration beans. {@link org.kinotic.os.KinoticOsApiLibrary}
 * excludes every {@code github} package from its scan, so this gate alone decides
 * whether the integration runs — {@code kinotic.disableGithub=true} switches it off
 * while the rest of the module stays up.
 */
@Configuration
@ComponentScan(basePackages = {"org.kinotic.os.api.config.github",
                               "org.kinotic.os.internal.api.services.github"})
@ConditionalOnProperty(value = "kinotic.disableGithub", havingValue = "false", matchIfMissing = true)
public class GithubConfiguration {
}
