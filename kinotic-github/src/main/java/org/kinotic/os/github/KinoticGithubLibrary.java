package org.kinotic.os.github;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the kinotic-github module: scans the {@code org.kinotic.os.github} package,
 * activates {@link org.kinotic.os.github.api.config.KinoticGithubProperties}, and
 * registers the GitHub App services, internal client, webhook dispatcher, and secret
 * bootstrap as Spring beans.
 * <p>
 * Conditional on {@code kinotic.disablePersistence=false} (the default) since the
 * services back onto Elasticsearch indexes managed by kinotic-domain.
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
@ConditionalOnProperty(value = "kinotic.disablePersistence", havingValue = "false", matchIfMissing = true)
public class KinoticGithubLibrary {
}
