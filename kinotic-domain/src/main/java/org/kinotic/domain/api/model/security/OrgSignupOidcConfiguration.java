package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A Kinotic-curated social IdP registration (GitHub today) that powers the "Continue with X" buttons on
 * the new-org signup page and on every organization's login page. Belongs to no organization,
 * because signup happens before one exists. Seeded via SQL migration and saved by the platform operator
 * only.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class OrgSignupOidcConfiguration extends ManagedOidcConfiguration {

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.ORG_SIGNUP;
    }
}
