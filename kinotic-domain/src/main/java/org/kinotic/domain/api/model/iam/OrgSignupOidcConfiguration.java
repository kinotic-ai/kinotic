package org.kinotic.domain.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Kinotic-curated social IdP configuration (Google, Microsoft Live, GitHub, etc.) that
 * powers the "Continue with X" buttons on both the new-org signup page and the
 * email-first org login fallback. Seeded via SQL migration; not editable through the
 * org admin UI.
 *
 * <p>Distinct from {@link OidcConfiguration}, which is per-org and referenced by
 * {@code Organization.ssoConfigId} or {@code Application.oidcConfigurationIds}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OrgSignupOidcConfiguration extends BaseOidcConfiguration {

    /**
     * Name of the OAuth client secret in the platform Azure Key Vault. The vault URI
     * is global config (one Key Vault per Kinotic deployment); the resolver always
     * fetches the latest version.
     */
    private String secretNameRef;
}
