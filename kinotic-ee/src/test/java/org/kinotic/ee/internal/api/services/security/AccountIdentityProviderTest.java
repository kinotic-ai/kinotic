package org.kinotic.ee.internal.api.services.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link EntraIdentityProvider} and {@link OktaIdentityProvider}: the issuer an organization's account
 * produces, and the account forms each refuses.
 */
class AccountIdentityProviderTest {

    private final EntraIdentityProvider entra = new EntraIdentityProvider();
    private final OktaIdentityProvider okta = new OktaIdentityProvider();

    @Test
    void anAccountFillsItsProvidersTemplate() {
        assertEquals("https://login.microsoftonline.com/9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718/v2.0",
                     entra.authority("9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718"));
        assertEquals("https://login.microsoftonline.com/common/v2.0", entra.authority("common"));
        assertEquals("https://login.microsoftonline.com/organizations/v2.0", entra.authority("organizations"));
        assertEquals("https://acme.okta.com", okta.authority("acme.okta.com"));
        assertEquals("https://acme.oktapreview.com", okta.authority("acme.oktapreview.com"));
        assertEquals("https://acme.okta-emea.com", okta.authority("acme.okta-emea.com"));
    }

    @Test
    void anAccountThatCouldLeaveTheProvidersHostsIsRefused() {
        for (String account : List.of("acme.okta.com/", "acme.okta.com.evil.example", "evil.example/acme.okta.com",
                                      "acme.okta.com:8443", "user@acme.okta.com", "acme.okta.com?x=1", "acme.okta.com#x",
                                      "acme.example.com", "ACME.okta.com", "", "a.b.okta.com")) {
            assertThrows(IllegalArgumentException.class, () -> okta.authority(account), account);
        }
        assertThrows(IllegalArgumentException.class, () -> okta.authority(null));
        for (String account : List.of("Common", "consumers", "9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718/v2.0",
                                      "9f1a4c2e3b7d4e8fa1b2c3d4e5f60718", "")) {
            assertThrows(IllegalArgumentException.class, () -> entra.authority(account), account);
        }
    }
}
