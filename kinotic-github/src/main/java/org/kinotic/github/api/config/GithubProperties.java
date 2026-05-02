package org.kinotic.github.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Non-secret + secret configuration for the single platform GitHub App.
 * <p>
 * In production the secret values ({@link #appPrivateKey}, {@link #webhookSecret})
 * are mounted by the AKS Secret Store CSI driver as environment variables and bound
 * via Spring's relaxed property binding (e.g. {@code KINOTIC_GITHUB_APPPRIVATEKEY}).
 * For local dev they can also be set via {@code application.yml} or any other
 * Spring-supported source.
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
     * RSA private-key PEM downloaded from the App's settings page. Both PKCS#1
     * ({@code BEGIN RSA PRIVATE KEY}) and PKCS#8 ({@code BEGIN PRIVATE KEY})
     * formats are accepted. Sign-once-cache-thereafter — see
     * {@code GitHubAppJwtFactory}.
     */
    @NotBlank
    private String appPrivateKey;

    /**
     * Webhook secret configured in the App's settings page. Used to HMAC-verify
     * inbound webhook bodies against the {@code X-Hub-Signature-256} header.
     */
    @NotBlank
    private String webhookSecret;
}
