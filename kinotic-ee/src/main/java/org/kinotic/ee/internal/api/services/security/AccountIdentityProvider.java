package org.kinotic.ee.internal.api.services.security;

import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.ee.services.OwnedIdentityProvider;

import java.util.regex.Pattern;

/**
 * An {@link OwnedIdentityProvider} with one issuer per account at the provider, built from an issuer template
 * the account fills, where the account must match a pattern that keeps the issuer on the provider's own hosts.
 */
public abstract class AccountIdentityProvider implements OwnedIdentityProvider {

    private static final String PROVIDER_ACCOUNT_PLACEHOLDER = "{providerAccount}";

    private final String authorityTemplate;
    private final Pattern providerAccountPattern;
    private final String providerAccountLabel;

    protected AccountIdentityProvider(String authorityTemplate, String providerAccountPattern, String providerAccountLabel) {
        this.authorityTemplate = authorityTemplate;
        this.providerAccountPattern = Pattern.compile(providerAccountPattern);
        this.providerAccountLabel = providerAccountLabel;
    }

    @Override
    public String authority(String providerAccount) {
        Validate.isTrue(providerAccount != null && providerAccountPattern.matcher(providerAccount).matches(),
                        "The %s is not in the form a %s provider expects", providerAccountLabel, type().key());
        return authorityTemplate.replace(PROVIDER_ACCOUNT_PLACEHOLDER, providerAccount);
    }
}
