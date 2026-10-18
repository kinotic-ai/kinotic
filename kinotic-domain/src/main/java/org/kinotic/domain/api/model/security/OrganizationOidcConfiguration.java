package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * An {@link OwnedOidcConfiguration} offered at the organization's own login: the enterprise SSO its
 * members sign in to the portal with, such as the organization's Okta or Entra directory.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class OrganizationOidcConfiguration extends OwnedOidcConfiguration {

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.ORGANIZATION;
    }
}
