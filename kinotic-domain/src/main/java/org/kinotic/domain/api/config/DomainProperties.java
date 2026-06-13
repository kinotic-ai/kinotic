package org.kinotic.domain.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.kinotic.core.api.config.SslProperties;

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
     * SSL/TLS configuration for all Vert.x HTTP servers.
     */
    private SslProperties ssl = new SslProperties();

    /**
     * Email / outbound-mail configuration.
     */
    private EmailProperties email = new EmailProperties();

    /**
     * Returns {@link #apiBaseUrl} when set, otherwise falls back to {@link #appBaseUrl}.
     * Use this when constructing OIDC {@code redirect_uri} values so split-origin deploys
     * (SPA + AKS on different domains) work without breaking same-origin defaults.
     */
    public String resolveApiBaseUrl() {
        return (apiBaseUrl != null && !apiBaseUrl.isBlank()) ? apiBaseUrl : appBaseUrl;
    }

}
