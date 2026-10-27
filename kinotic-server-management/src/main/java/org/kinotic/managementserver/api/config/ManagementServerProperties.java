package org.kinotic.managementserver.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

/**
 * The OAuth issuer the management server's browser flows use, the Turnstile site its open routes are guarded by, and
 * the ways the portal offers to sign up, {@code kinotic.managementServer.*}, set per deployment. The URLs the
 * management server's API and the portal are reached at are {@code kinotic.managementApi.apiBaseUrl} and
 * {@code kinotic.domain.portalBaseUrl}.
 */
@Getter
@Setter
public class ManagementServerProperties {

    /**
     * Base URL of the management server's OAuth 2.1 surface as the internet reaches it. Set only where that differs from
     * {@code kinotic.managementApi.apiBaseUrl}: a development gateway whose OAuth surface is tunnelled while its OIDC
     * callbacks stay on {@code localhost}.
     */
    private String issuerBaseUrl;

    /** The Cloudflare Turnstile site the portal's find-my-organizations form presents and this server verifies. */
    @Valid
    private TurnstileProperties turnstile = new TurnstileProperties();

    /**
     * Whether signing up with an email and a password is offered, as a demonstration server does: the portal's sign-up
     * page offers it in place of GitHub alone, and {@code POST /api/auth/org/signup} accepts it.
     */
    private boolean passwordSignUp = false;

    /**
     * The OAuth issuer: {@link #issuerBaseUrl} where set, otherwise {@code managementApiBaseUrl}.
     *
     * @param managementApiBaseUrl the management server's API base URL, {@code kinotic.managementApi.apiBaseUrl}
     */
    public String resolveIssuerBaseUrl(String managementApiBaseUrl) {
        return issuerBaseUrl != null && !issuerBaseUrl.isBlank() ? issuerBaseUrl : managementApiBaseUrl;
    }
}
