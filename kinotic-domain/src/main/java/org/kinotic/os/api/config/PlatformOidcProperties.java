package org.kinotic.os.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bootstraps the platform-wide OIDC configurations (Google, Microsoft/Entra-as-social, etc.)
 * that show up as login/signup buttons across every org. Operators set the non-secret fields
 * here in helm values; the actual {@code clientSecret} is read from a per-configId file under
 * {@link #secretsPath} (CSI-mounted from Key Vault on Azure, k8s Secret on KinD,
 * a {@code ~/.kinotic/dev-oidc-secrets} file in bare local dev). Restarting kinotic-server
 * picks up changes.
 * <p>
 * Bound under {@code kinotic.oidc} via {@link KinoticDomainProperties}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PlatformOidcProperties {

    /**
     * Mount path containing one file per platform provider, named after the provider's
     * {@link PlatformProviderEntry#getId()}. Each file's contents are the raw client secret.
     * Default {@code /etc/kinotic/oidc-client-secrets} matches the CSI / k8s Secret mount.
     */
    private Path secretsPath = Path.of("/etc/kinotic/oidc-client-secrets");

    private List<PlatformProviderEntry> platformProviders = new ArrayList<>();

    @Getter
    @Setter
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class PlatformProviderEntry {
        /**
         * Stable id for this configuration. Matters because the OIDC redirect URI registered
         * with the IdP becomes {@code <appBaseUrl>/api/login/callback/<id>} — changing the id
         * after registration breaks the callback.
         */
        private String id;

        /** Human-readable name, e.g. "Microsoft" or "Google". */
        private String name;

        /** {@link org.kinotic.os.api.model.iam.OidcProviderKind} key — "google", "azure-ad", etc. */
        private String provider;

        /** OAuth2 client id issued by the provider when Kinotic was registered. */
        private String clientId;

        /** OIDC issuer URL, e.g. {@code https://login.microsoftonline.com/<tenant>/v2.0}. */
        private String authority;

        /** Optional audience override; defaults to {@code clientId} when null. */
        private String audience;
    }
}
