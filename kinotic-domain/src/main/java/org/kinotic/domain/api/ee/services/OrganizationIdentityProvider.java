package org.kinotic.domain.api.ee.services;

import org.kinotic.domain.api.model.security.OidcProviderType;

/**
 * An identity provider an organization may sign its people in with through a client of its own registration, at
 * its own login, an application's login or a tenant's login. The provider turns the organization's account at the
 * provider into the issuer URL the server connects to, so an organization never names a URL and the server only
 * ever connects to the provider's own hosts.
 *
 * <p>This is an Enterprise Extension Point: Production Use of any implementation other than the ones in
 * {@code kinotic-ee} or the unmodified {@code GoogleIdentityProvider} requires a Kinotic Enterprise License. See the
 * Kinotic Additional Limitations in {@code LICENSE.md}.
 */
public interface OrganizationIdentityProvider {

    /**
     * The provider type this provider signs in with; one provider exists for each type offered to organizations.
     */
    OidcProviderType type();

    /**
     * The issuer URL of an organization's account at this provider.
     *
     * @param providerAccount the organization's account at the provider, such as an Okta domain, or {@code null} for a
     *                        provider with one issuer for everyone
     * @return the issuer URL
     * @throws IllegalArgumentException when the account is missing, given to a provider that takes none, or not in
     *                                  the form the provider expects
     */
    String authority(String providerAccount);
}
