package org.kinotic.os.github.api.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.nio.file.Path;

/**
 * Non-secret configuration for the single platform GitHub App. The App private key (PEM)
 * and webhook secret are read from files under {@link #secretsPath} on startup and
 * promoted into {@code SecretStorageService} (mirrors the OIDC client-secret pattern).
 * <p>
 * Bound under {@code kinotic.github.*} via {@link KinoticGithubProperties}; required
 * fields are validated at boot via Jakarta Bean Validation.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GithubProperties {

    /** Numeric GitHub App id (from the App's settings page). */
    @NotBlank
    private String appId;

    /**
     * URL slug for the App, used to build the install URL
     * {@code https://github.com/apps/<appSlug>/installations/new}.
     */
    @NotBlank
    private String appSlug;

    /**
     * Mount path containing the secret files {@code githubAppPrivateKey} (PEM) and
     * {@code githubWebhookSecret}. Default matches the CSI / k8s Secret mount.
     */
    @NotNull
    private Path secretsPath = Path.of("/etc/kinotic/github-secrets");
}
