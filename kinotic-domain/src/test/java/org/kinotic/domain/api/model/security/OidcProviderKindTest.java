package org.kinotic.domain.api.model.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link OidcProviderKind#authority}: the issuer an organization's account produces for each kind
 * offered to organizations, the account forms each kind refuses, and that a kind without a template is
 * not offered at all.
 */
class OidcProviderKindTest {

    @Test
    void anAccountFillsItsKindsTemplate() {
        assertEquals("https://login.microsoftonline.com/9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718/v2.0",
                     OidcProviderKind.AZURE_AD.authority("9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718"));
        assertEquals("https://acme.okta.com", OidcProviderKind.OKTA.authority("acme.okta.com"));
        assertEquals("https://acme.oktapreview.com", OidcProviderKind.OKTA.authority("acme.oktapreview.com"));
        assertEquals("https://acme.okta-emea.com", OidcProviderKind.OKTA.authority("acme.okta-emea.com"));
        assertEquals("https://accounts.google.com", OidcProviderKind.GOOGLE.authority(null));
        assertEquals("https://accounts.google.com", OidcProviderKind.GOOGLE.authority(""));
    }

    @Test
    void anAccountThatCouldLeaveTheProvidersHostsIsRefused() {
        for (String account : List.of("acme.okta.com/", "acme.okta.com.evil.example", "evil.example/acme.okta.com",
                                      "acme.okta.com:8443", "user@acme.okta.com", "acme.okta.com?x=1", "acme.okta.com#x",
                                      "acme.example.com", "ACME.okta.com", "", "a.b.okta.com")) {
            assertThrows(IllegalArgumentException.class, () -> OidcProviderKind.OKTA.authority(account), account);
        }
        assertThrows(IllegalArgumentException.class, () -> OidcProviderKind.OKTA.authority(null));
        for (String account : List.of("common", "organizations", "9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718/v2.0",
                                      "9f1a4c2e3b7d4e8fa1b2c3d4e5f60718", "")) {
            assertThrows(IllegalArgumentException.class, () -> OidcProviderKind.AZURE_AD.authority(account), account);
        }
        assertThrows(IllegalArgumentException.class, () -> OidcProviderKind.GOOGLE.authority("acme"));
    }

    @Test
    void aKindWithoutATemplateIsNotOfferedToOrganizations() {
        assertTrue(OidcProviderKind.AZURE_AD.offeredToOrganizations());
        assertTrue(OidcProviderKind.OKTA.offeredToOrganizations());
        assertTrue(OidcProviderKind.GOOGLE.offeredToOrganizations());
        assertNull(OidcProviderKind.GOOGLE.providerAccountLabel());
        assertEquals("Okta domain", OidcProviderKind.OKTA.providerAccountLabel());
        for (OidcProviderKind kind : List.of(OidcProviderKind.GITHUB, OidcProviderKind.KEYCLOAK, OidcProviderKind.OIDC,
                                             OidcProviderKind.AUTH0, OidcProviderKind.AMAZON_COGNITO)) {
            assertFalse(kind.offeredToOrganizations(), kind.key());
            assertThrows(IllegalArgumentException.class, () -> kind.authority("acme"), kind.key());
        }
    }
}
