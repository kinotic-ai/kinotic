package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A Kinotic-curated social IdP registration (Google, Microsoft) offered to applications for their users'
 * sign-in: an {@link ApplicationOidcConfiguration} of the same provider kind whose
 * {@link ClientRegistrationType} is {@link ClientRegistrationType#MANAGED} signs in through it, and the
 * application's users then sign in under Kinotic's name on the provider's consent screen, with no registration
 * of the application's own. Belongs to no organization. Seeded via SQL migration and saved by a system user
 * only.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class ApplicationSocialOidcConfiguration extends ManagedOidcConfiguration {

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.APP_SOCIAL;
    }
}
