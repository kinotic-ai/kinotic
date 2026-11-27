package org.kinotic.management.internal.config;

import org.kinotic.management.api.config.GithubProperties;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.config.ManagementApiProperties;
import org.kinotic.management.internal.api.services.github.client.GitHubAppJwtFactory;
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
     * Signs the JWTs of the GitHub App organizations install, from {@code kinotic.managementApi.github}.
     */
    @Bean
    public GitHubAppJwtFactory gitHubAppJwtFactory(GithubProperties githubProperties) {
        return new GitHubAppJwtFactory(githubProperties.getAppId(), githubProperties.getAppPrivateKey());
    }

    /**
     * Makes the ManagementApiProperties bean available for use by other beans without needing to
     * inject {@link KinoticManagementApiProperties}
     */
    @Bean
    public ManagementApiProperties managementApiProperties(KinoticManagementApiProperties properties) {
        return properties.getManagementApi();
    }

}
