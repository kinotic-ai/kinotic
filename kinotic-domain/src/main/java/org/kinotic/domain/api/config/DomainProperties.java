package org.kinotic.domain.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.kinotic.domain.api.model.AppHost;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
     * Base URL every application's API host is a label under (scheme + domain + optional port, no
     * trailing slash): with {@code https://apps-api.kinotic.ai}, application {@code orders} of
     * organization {@code acme} is reached at {@code https://acme--orders.apps-api.kinotic.ai}.
     */
    @NotBlank
    private String appApiBaseUrl = "http://localhost:58505";

    /**
     * Origins admitted as a UI of every application, for UIs a developer serves from their own machine, such as
     * {@code http://localhost:\d+}. When unset, an application's login is made only from its published sites.
     */
    private Pattern localAppUiOriginPattern = null;

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

    /**
     * Returns {@link #apiBaseUrl} when set, otherwise falls back to {@link #appBaseUrl}.
     * Use this when constructing OIDC {@code redirect_uri} values so split-origin deploys
     * (SPA + AKS on different domains) work without breaking same-origin defaults.
     */
    public String resolveApiBaseUrl() {
        return (apiBaseUrl != null && !apiBaseUrl.isBlank()) ? apiBaseUrl : appBaseUrl;
    }

    /**
     * The URL a browser reaches the application's API on: its {@link AppHost#label()} under
     * {@link #appApiBaseUrl}, with that URL's scheme and port.
     */
    public String resolveAppApiUrl(AppHost appHost) {
        URI base = URI.create(appApiBaseUrl);
        return base.getScheme() + "://" + appHost.label() + "." + base.getRawAuthority();
    }

    /**
     * The application whose API host {@code host} is, a label under {@link #appApiBaseUrl}'s domain, or
     * {@code null} when {@code host} is no application's API host.
     */
    public AppHost resolveAppHost(String host) {
        String domainSuffix = "." + URI.create(appApiBaseUrl).getHost().toLowerCase(Locale.ROOT);
        String name = host.toLowerCase(Locale.ROOT);
        AppHost ret = null;
        if (name.endsWith(domainSuffix)) {
            ret = AppHost.fromLabel(name.substring(0, name.length() - domainSuffix.length()));
        }
        return ret;
    }

    /**
     * Returns {@link OAuthProperties#getIssuerBaseUrl()} when set, otherwise falls back to
     * {@link #resolveApiBaseUrl()}. Use this when publishing the OAuth 2.1 surface MCP hosts
     * discover, which their backends reach directly rather than through the browser.
     */
    public String resolveIssuerBaseUrl() {
        String issuerBaseUrl = oauth.getIssuerBaseUrl();
        return (issuerBaseUrl != null && !issuerBaseUrl.isBlank()) ? issuerBaseUrl : resolveApiBaseUrl();
    }

}
