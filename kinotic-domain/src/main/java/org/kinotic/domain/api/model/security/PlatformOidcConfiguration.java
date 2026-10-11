package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A Kinotic-curated social IdP configuration (Google, Microsoft Live, GitHub, etc.) that powers
 * the "Continue with X" buttons on both the new-org signup page and the email-first org login
 * fallback. Belongs to no organization, because signup happens before one exists. Seeded via SQL
 * migration and saved by the platform operator only. Its client secret is one the operator
 * placed in the platform's secret storage under the name {@link #secretNameRef}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class PlatformOidcConfiguration extends OidcConfiguration {

    /**
     * Name of the OAuth client secret in the platform's secret storage — the Azure Key Vault of
     * the deployment, or a {@code KINOTIC_AKV_*} property in development; the latest version is
     * read at sign-in. {@code null} for a public client.
     */
    private String secretNameRef;

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.PLATFORM;
    }
}
