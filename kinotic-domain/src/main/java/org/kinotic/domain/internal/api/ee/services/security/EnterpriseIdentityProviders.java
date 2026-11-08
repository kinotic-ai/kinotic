package org.kinotic.domain.internal.api.ee.services.security;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.kinotic.domain.internal.api.ee.license.CurrentLicenseService;
import org.kinotic.domain.api.model.license.EnterpriseFeature;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * The identity providers an organization may sign people in with through a client of its own registration under a
 * Kinotic Enterprise License: Microsoft Entra and Okta. Each turns the organization's account at the provider into
 * the issuer URL the server connects to, and accepts only an account that keeps the issuer on the provider's own
 * hosts.
 */
@Component
@RequiredArgsConstructor
public class EnterpriseIdentityProviders {

    // the Entra directory named by its tenant id, or common for every Microsoft account and organizations for every
    // work or school account
    private static final Pattern ENTRA_ACCOUNT =
            Pattern.compile("common|organizations|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    // the Okta org named by its Okta domain, on any of Okta's own domains
    private static final Pattern OKTA_ACCOUNT =
            Pattern.compile("[a-z0-9-]{1,63}\\.(okta\\.com|oktapreview\\.com|okta-emea\\.com)");

    private final CurrentLicenseService currentLicenseService;

    /**
     * Whether an organization may configure a provider of this type with a client of its own: the type is one of
     * these providers and the license turns {@link EnterpriseFeature#ENTERPRISE_IDENTITY_PROVIDERS} on now.
     */
    public boolean supports(OidcProviderType type) {
        return (type == OidcProviderType.AZURE_AD || type == OidcProviderType.OKTA)
                && currentLicenseService.allows(EnterpriseFeature.ENTERPRISE_IDENTITY_PROVIDERS);
    }

    /**
     * The issuer URL of an organization's account at a provider.
     *
     * @param type            a provider type {@link #supports(OidcProviderType)} accepts
     * @param providerAccount the organization's account at the provider: the Entra directory (tenant) id,
     *                        {@code common} or {@code organizations}, or the Okta domain
     * @return the issuer URL
     * @throws IllegalArgumentException when the type is not supported, or the account is missing or not in the form
     *                                  the provider expects
     */
    public String authority(OidcProviderType type, String providerAccount) {
        String ret;
        if (type == OidcProviderType.AZURE_AD) {
            requireAccount(providerAccount, ENTRA_ACCOUNT, "Directory (tenant) ID, or common or organizations", type);
            ret = "https://login.microsoftonline.com/" + providerAccount + "/v2.0";
        } else if (type == OidcProviderType.OKTA) {
            requireAccount(providerAccount, OKTA_ACCOUNT, "Okta domain", type);
            ret = "https://" + providerAccount;
        } else {
            throw new IllegalArgumentException("A " + type.key() + " provider is not a Kinotic Enterprise identity provider");
        }
        return ret;
    }

    private static void requireAccount(String providerAccount, Pattern pattern, String label, OidcProviderType type) {
        Validate.isTrue(providerAccount != null && pattern.matcher(providerAccount).matches(),
                        "The %s is not in the form a %s provider expects", label, type.key());
    }
}
