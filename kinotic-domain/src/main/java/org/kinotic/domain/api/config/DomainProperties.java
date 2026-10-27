package org.kinotic.domain.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
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
     * Paths to system-level secret files (the JWT signing keys).
     * Files are mounted into the pod by the Azure Key Vault CSI driver in production or
     * by a Kubernetes Secret volume locally, and watched for changes so rotation flows
     * through without a restart.
     */
    private SystemSecretsProperties systemSecrets = new SystemSecretsProperties();

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
     * The domain organizations' published sites are served under and the edge that serves them.
     */
    @Valid
    private PublishedSitesProperties publishedSites = new PublishedSitesProperties();

    /**
     * Base URL every application's API host is a label under, as a browser reaches it (scheme + domain + optional
     * port, no trailing slash): with {@code https://apps-api.kinotic.io}, application {@code orders} of organization
     * {@code acme} is served at {@code https://acme--orders.apps-api.kinotic.io}, which is also its OAuth issuer, the
     * host its UIs are built against, and the host of the callback of each of its OIDC configurations. The base
     * URL itself is the shared host a sign-in through one of the system's social registrations returns to.
     */
    private String appApiBaseUrl;

    /**
     * Base URL of the portal as a browser reaches it (no trailing slash): where the management server's browser
     * flows send the user, and the base of the verification, invite and sign-in links in the platform's emails.
     */
    private String portalBaseUrl;

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

    /**
     * The origin of the portal's pages, the scheme and authority of {@link #getPortalBaseUrl()}, or {@code null} when
     * no portal is set.
     */
    public String portalOrigin() {
        String ret = null;
        if (portalBaseUrl != null) {
            URI portal = URI.create(portalBaseUrl);
            ret = portal.getScheme() + "://" + portal.getAuthority();
        }
        return ret;
    }

    public boolean hasElasticUsernameAndPassword(){
        return elasticUsername != null && !elasticUsername.isBlank() && elasticPassword != null && !elasticPassword.isBlank();
    }

}
