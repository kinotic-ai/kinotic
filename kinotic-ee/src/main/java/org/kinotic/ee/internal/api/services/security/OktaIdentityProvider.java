package org.kinotic.ee.internal.api.services.security;

import org.kinotic.domain.api.model.security.OidcProviderType;

/**
 * Okta, where the account is the Okta org named by its Okta domain, on any of Okta's own domains.
 */
public class OktaIdentityProvider extends AccountIdentityProvider {

    public OktaIdentityProvider() {
        super("https://{providerAccount}",
              "[a-z0-9-]{1,63}\\.(okta\\.com|oktapreview\\.com|okta-emea\\.com)",
              "Okta domain");
    }

    @Override
    public OidcProviderType type() {
        return OidcProviderType.OKTA;
    }
}
