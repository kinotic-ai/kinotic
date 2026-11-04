package org.kinotic.domain.internal.api.services.security;

import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.ee.services.OrganizationIdentityProvider;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.springframework.stereotype.Component;

/**
 * Google, whose Workspace and consumer accounts share one issuer, so an organization names no account.
 */
@Component
public class GoogleIdentityProvider implements OrganizationIdentityProvider {

    private static final String AUTHORITY = "https://accounts.google.com";

    @Override
    public OidcProviderType type() {
        return OidcProviderType.GOOGLE;
    }

    @Override
    public String authority(String providerAccount) {
        Validate.isTrue(providerAccount == null || providerAccount.isBlank(), "A %s provider takes no account", type().key());
        return AUTHORITY;
    }
}
