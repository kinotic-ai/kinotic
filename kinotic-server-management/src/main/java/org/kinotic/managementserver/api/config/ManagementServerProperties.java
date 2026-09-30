package org.kinotic.managementserver.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The URLs the management server is reached at, {@code kinotic.managementServer.*}, set per deployment. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "kinotic.managementServer")
public class ManagementServerProperties {

    /** Base URL of the management server's API as a browser reaches it: the base of its OIDC {@code redirect_uri}s. */
    @NotBlank
    private String apiBaseUrl;

    /**
     * Base URL of the management server's OAuth 2.1 surface as the internet reaches it. Set only where that differs from
     * {@link #apiBaseUrl}: a development gateway whose OAuth surface is tunnelled while its OIDC callbacks stay
     * on {@code localhost}.
     */
    private String issuerBaseUrl;

    /** Base URL of the portal, where the management server's browser flows send the user. */
    @NotBlank
    private String portalBaseUrl;

    /** The OAuth issuer: {@link #issuerBaseUrl} where set, otherwise {@link #apiBaseUrl}. */
    public String resolveIssuerBaseUrl() {
        return issuerBaseUrl != null && !issuerBaseUrl.isBlank() ? issuerBaseUrl : apiBaseUrl;
    }
}
