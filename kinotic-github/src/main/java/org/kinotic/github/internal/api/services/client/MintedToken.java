package org.kinotic.github.internal.api.services.client;

import java.time.Instant;

/**
 * GitHub installation access token returned by
 * {@link GitHubApiClient#createInstallationToken}. The token is short-lived (~1
 * hour); {@code expiresAt} is GitHub's authoritative wall-clock expiry.
 */
public record MintedToken(String token, Instant expiresAt) {}
