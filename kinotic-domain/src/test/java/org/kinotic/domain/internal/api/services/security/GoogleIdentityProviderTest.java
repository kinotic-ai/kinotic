package org.kinotic.domain.internal.api.services.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link GoogleIdentityProvider}: one issuer for every organization, which names no account.
 */
class GoogleIdentityProviderTest {

    private final GoogleIdentityProvider provider = new GoogleIdentityProvider();

    @Test
    void everyOrganizationSharesGooglesIssuer() {
        assertEquals("https://accounts.google.com", provider.authority(null));
        assertEquals("https://accounts.google.com", provider.authority(""));
    }

    @Test
    void anAccountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> provider.authority("acme"));
    }
}
