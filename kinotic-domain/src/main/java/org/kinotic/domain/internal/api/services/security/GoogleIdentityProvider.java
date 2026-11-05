package org.kinotic.domain.internal.api.services.security;

import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.springframework.stereotype.Component;

/**
 * Google, whose Workspace and consumer accounts share one issuer, so an organization names no account.
 */
@Component
public class GoogleIdentityProvider {

    private static final String AUTHORITY = "https://accounts.google.com";

    /**
     * The issuer URL Google signs everyone in with.
     *
     * @param providerAccount {@code null} or blank, since Google takes no account
     * @return the issuer URL
     * @throws IllegalArgumentException when an account is given
     */
    public String authority(String providerAccount) {
        Validate.isTrue(providerAccount == null || providerAccount.isBlank(), "A %s provider takes no account", OidcProviderType.GOOGLE.key());
        return AUTHORITY;
    }
}
