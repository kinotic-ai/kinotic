package org.kinotic.os.github.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Non-secret configuration for the single platform GitHub App. The App private key (PEM)
 * and webhook secret are read from files under {@link #secretsPath} on startup and
 * promoted into {@code SecretStorageService} (mirrors the OIDC client-secret pattern).
 * <p>
 * Bound under {@code kinotic.github.*} via {@link KinoticGithubProperties}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GithubProperties {

    /** Numeric GitHub App id (from the App's settings page). */
    private String appId;

    /**
     * URL slug for the App, used to build the install URL
     * {@code https://github.com/apps/<appSlug>/installations/new}.
     */
    private String appSlug;

    /**
     * Refresh installation tokens this much earlier than their stated expiry. Avoids
     * handing a near-expired token to a worker doing a long clone.
     */
    private Duration tokenSkew = Duration.ofMinutes(5);

    /**
     * Never return an installation token with less than this much life remaining; refresh
     * before returning.
     */
    private Duration minReturnedTokenLifetime = Duration.ofMinutes(10);

    /**
     * Maximum size of a webhook body. GitHub's documented ceiling is 25 MiB; anything
     * larger is rejected with 413 before HMAC runs.
     */
    private int webhookBodyLimitBytes = 25 * 1024 * 1024;

    /**
     * Mount path containing the secret files {@code githubAppPrivateKey} (PEM) and
     * {@code githubWebhookSecret}. Default matches the CSI / k8s Secret mount.
     */
    private Path secretsPath = Path.of("/etc/kinotic/github-secrets");
}
