package org.kinotic.domain.internal.api.ee.services.security;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.security.OidcProviderType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link EnterpriseIdentityProviders}: the issuer an organization's Microsoft Entra or Okta account
 * produces, and the account forms each refuses.
 */
class EnterpriseIdentityProvidersTest {

    private final EnterpriseIdentityProviders providers = new EnterpriseIdentityProviders();

    @Test
    void anAccountFillsItsProvidersTemplate() {
        assertEquals("https://login.microsoftonline.com/9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718/v2.0",
                     providers.authority(OidcProviderType.AZURE_AD, "9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718"));
        assertEquals("https://login.microsoftonline.com/common/v2.0", providers.authority(OidcProviderType.AZURE_AD, "common"));
        assertEquals("https://login.microsoftonline.com/organizations/v2.0", providers.authority(OidcProviderType.AZURE_AD, "organizations"));
        assertEquals("https://acme.okta.com", providers.authority(OidcProviderType.OKTA, "acme.okta.com"));
        assertEquals("https://acme.oktapreview.com", providers.authority(OidcProviderType.OKTA, "acme.oktapreview.com"));
        assertEquals("https://acme.okta-emea.com", providers.authority(OidcProviderType.OKTA, "acme.okta-emea.com"));
    }

    @Test
    void anAccountThatCouldLeaveTheProvidersHostsIsRefused() {
        for (String account : List.of("acme.okta.com/", "acme.okta.com.evil.example", "evil.example/acme.okta.com",
                                      "acme.okta.com:8443", "user@acme.okta.com", "acme.okta.com?x=1", "acme.okta.com#x",
                                      "acme.example.com", "ACME.okta.com", "", "a.b.okta.com")) {
            assertThrows(IllegalArgumentException.class, () -> providers.authority(OidcProviderType.OKTA, account), account);
        }
        assertThrows(IllegalArgumentException.class, () -> providers.authority(OidcProviderType.OKTA, null));
        for (String account : List.of("Common", "consumers", "9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718/v2.0",
                                      "9f1a4c2e3b7d4e8fa1b2c3d4e5f60718", "")) {
            assertThrows(IllegalArgumentException.class, () -> providers.authority(OidcProviderType.AZURE_AD, account), account);
        }
    }

    @Test
    void onlyEntraAndOktaAreSupported() {
        assertTrue(providers.supports(OidcProviderType.AZURE_AD));
        assertTrue(providers.supports(OidcProviderType.OKTA));
        assertFalse(providers.supports(OidcProviderType.GOOGLE));
        assertThrows(IllegalArgumentException.class, () -> providers.authority(OidcProviderType.GOOGLE, null));
    }
}
