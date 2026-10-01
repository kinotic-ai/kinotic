package org.kinotic.domain.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

/**
 *
 * Created By Navíd Mitchell 🤪on 4/26/26
 */
@Getter
@Setter
public class DomainProperties {

    /**
     * Email / outbound-mail configuration.
     */
    private EmailProperties email = new EmailProperties();

    /**
     * OAuth 2.1 authorization-server configuration.
     */
    @Valid
    private OAuthProperties oauth = new OAuthProperties();

    /**
     * Entity storage configuration.
     */
    @Valid
    private DomainPersistenceProperties persistence = new DomainPersistenceProperties();

    /**
     * Secret storage configuration. If null, an in-memory backend is used.
     */
    private SecretStorageProperties secretStorage;

    /**
     * The Elasticsearch cluster that stores the platform's domain objects: organizations, applications, projects,
     * IAM, {@code EntityDefinition}s and the rest of the {@code kinotic_*} indices.
     */
    @Valid
    @NotNull
    private ElasticClusterProperties elastic = new ElasticClusterProperties();

    /**
     * The interval to check the health of the elastic clusters
     */
    @NotNull
    private Duration elasticHealthCheckInterval = Duration.ofMinutes(1);

}
