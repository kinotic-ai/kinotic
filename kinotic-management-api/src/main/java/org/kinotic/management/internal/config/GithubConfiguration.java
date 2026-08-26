package org.kinotic.management.internal.config;

import org.kinotic.management.KinoticManagementApiLibrary;
import org.kinotic.management.api.config.github.GithubProperties;
import org.kinotic.management.api.config.github.KinoticManagementApiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the GitHub integration beans. {@link KinoticManagementApiLibrary}
 * excludes every {@code github} package from its scan, so this gate alone decides
 * whether the integration runs — {@code kinotic.managementApi.github.disable=true} switches it off
 * while the rest of the module stays up.
 */
@Configuration
@ComponentScan(basePackages = {"org.kinotic.management.api.config.github",
                               "org.kinotic.management.internal.api.services.github"})
@ConditionalOnProperty(value = "kinotic.managementApi.github.disable", havingValue = "false", matchIfMissing = true)
public class GithubConfiguration {

    /**
     * Makes the GithubProperties bean available for use by other beans without needing to
     * inject {@link KinoticManagementApiProperties}
     */
    @Bean
    public GithubProperties githubProperties(KinoticManagementApiProperties properties) {
        return properties.getManagementApi().getGithub();
    }

}
