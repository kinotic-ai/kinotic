package org.kinotic.management.internal.config;

import org.kinotic.management.api.config.github.GithubProperties;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
