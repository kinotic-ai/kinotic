package org.kinotic.domain.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.List;

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

    @NotNull
    private Duration elasticConnectionTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration elasticSocketTimeout = Duration.ofMinutes(1);

    /**
     * The interval to check the health of the elastic cluster
     */
    @NotNull
    private Duration elasticHealthCheckInterval = Duration.ofMinutes(1);

    @NotNull
    private List<ElasticConnectionInfo> elasticConnections = List.of(new ElasticConnectionInfo());

    private String elasticUsername = null;

    private String elasticPassword = null;

    public boolean hasElasticUsernameAndPassword(){
        return elasticUsername != null && !elasticUsername.isBlank() && elasticPassword != null && !elasticPassword.isBlank();
    }

}
