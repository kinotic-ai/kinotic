package org.kinotic.domain.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
     * Public-facing base URL of the SPA (scheme + host + optional port, no trailing slash).
     * Used to build absolute links to user-visible SPA routes — e.g. the verification email link
     * sent to new sign-ups, or post-login redirects after an OIDC roundtrip.
     */
    @NotBlank
    private String appBaseUrl = "http://localhost:9090";

    /**
     * Public-facing base URL the backend serves its REST endpoints under (scheme + host + optional
     * port, no trailing slash). Used as the OIDC {@code redirect_uri} so the IdP returns the
     * browser to {@code /api/auth/org/login/social/callback/<id>} on the backend, not on the SPA.
     * <p>
     * Set this when the SPA and backend live on different origins (Azure Static Web Apps + AKS).
     * When left null the platform falls back to {@link #appBaseUrl} — fine for dev and any deploy
     * where the SPA is served from the same origin as the API.
     */
    private String apiBaseUrl = null;

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

    /**
     * Returns {@link #apiBaseUrl} when set, otherwise falls back to {@link #appBaseUrl}.
     * Use this when constructing OIDC {@code redirect_uri} values so split-origin deploys
     * (SPA + AKS on different domains) work without breaking same-origin defaults.
     */
    public String resolveApiBaseUrl() {
        return (apiBaseUrl != null && !apiBaseUrl.isBlank()) ? apiBaseUrl : appBaseUrl;
    }

    /**
     * Returns {@link OAuthProperties#getIssuerBaseUrl()} when set, otherwise falls back to
     * {@link #resolveApiBaseUrl()}. Use this when publishing the OAuth 2.1 surface MCP hosts
     * discover, which their backends reach directly rather than through the browser.
     */
    // FIXME: shotgun surgery — one of five places that know the OAuth surface has its own base URL.
    // See "OAuth base URL split" in docs/NavidNotes.md for the topologies that would remove it.
    public String resolveIssuerBaseUrl() {
        String issuerBaseUrl = oauth.getIssuerBaseUrl();
        return (issuerBaseUrl != null && !issuerBaseUrl.isBlank()) ? issuerBaseUrl : resolveApiBaseUrl();
    }

}
