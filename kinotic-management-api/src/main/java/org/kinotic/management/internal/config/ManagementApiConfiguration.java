package org.kinotic.management.internal.config;

import org.kinotic.management.api.config.GithubProperties;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.config.LokiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ManagementApiConfiguration {

    /**
     * Makes the GithubProperties bean available for use by other beans without needing to
     * inject {@link KinoticManagementApiProperties}
     */
    @Bean
    public GithubProperties githubProperties(KinoticManagementApiProperties properties) {
        return properties.getManagementApi().getGithub();
    }

    /**
     * Makes the LokiProperties bean available for use by other beans without needing to
     * inject {@link KinoticManagementApiProperties}
     */
    @Bean
    public LokiProperties lokiProperties(KinoticManagementApiProperties properties) {
        return properties.getManagementApi().getLoki();
    }

}
