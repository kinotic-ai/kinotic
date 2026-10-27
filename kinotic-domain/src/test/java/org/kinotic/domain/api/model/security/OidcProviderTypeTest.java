package org.kinotic.domain.api.model.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link OidcProviderType#authority}: the issuer an organization's account produces for each kind
 * offered to organizations, the account forms each kind refuses, the Entra accounts that admit every
 * directory, and that a kind without a template is not offered at all.
 */
class OidcProviderKindTest {

    @Test
    void anAccountFillsItsKindsTemplate() {
        assertEquals("https://login.microsoftonline.com/9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718/v2.0",
                     OidcProviderType.AZURE_AD.authority("9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718"));
        assertEquals("https://login.microsoftonline.com/common/v2.0", OidcProviderType.AZURE_AD.authority("common"));
        assertEquals("https://login.microsoftonline.com/organizations/v2.0", OidcProviderType.AZURE_AD.authority("organizations"));
        assertEquals("https://acme.okta.com", OidcProviderType.OKTA.authority("acme.okta.com"));
        assertEquals("https://acme.oktapreview.com", OidcProviderType.OKTA.authority("acme.oktapreview.com"));
        assertEquals("https://acme.okta-emea.com", OidcProviderType.OKTA.authority("acme.okta-emea.com"));
        assertEquals("https://accounts.google.com", OidcProviderType.GOOGLE.authority(null));
        assertEquals("https://accounts.google.com", OidcProviderType.GOOGLE.authority(""));
    }

    @Test
    void anAccountThatCouldLeaveTheProvidersHostsIsRefused() {
        for (String account : List.of("acme.okta.com/", "acme.okta.com.evil.example", "evil.example/acme.okta.com",
                                      "acme.okta.com:8443", "user@acme.okta.com", "acme.okta.com?x=1", "acme.okta.com#x",
                                      "acme.example.com", "ACME.okta.com", "", "a.b.okta.com")) {
            assertThrows(IllegalArgumentException.class, () -> OidcProviderType.OKTA.authority(account), account);
        }
        assertThrows(IllegalArgumentException.class, () -> OidcProviderType.OKTA.authority(null));
        for (String account : List.of("Common", "consumers", "9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718/v2.0",
                                      "9f1a4c2e3b7d4e8fa1b2c3d4e5f60718", "")) {
            assertThrows(IllegalArgumentException.class, () -> OidcProviderType.AZURE_AD.authority(account), account);
        }
        assertThrows(IllegalArgumentException.class, () -> OidcProviderType.GOOGLE.authority("acme"));
    }

    @Test
    void onlyEntrasCommonAndOrganizationsAdmitEveryDirectory() {
        assertTrue(OidcProviderType.AZURE_AD.admitsEveryDirectory("common"));
        assertTrue(OidcProviderType.AZURE_AD.admitsEveryDirectory("organizations"));
        assertFalse(OidcProviderType.AZURE_AD.admitsEveryDirectory("9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718"));
        assertFalse(OidcProviderType.OKTA.admitsEveryDirectory("common"));
    }

    @Test
    void aKindWithoutATemplateIsNotOfferedToOrganizations() {
        assertTrue(OidcProviderType.AZURE_AD.offeredToOrganizations());
        assertTrue(OidcProviderType.OKTA.offeredToOrganizations());
        assertTrue(OidcProviderType.GOOGLE.offeredToOrganizations());
        assertNull(OidcProviderType.GOOGLE.providerAccountLabel());
        assertEquals("Okta domain", OidcProviderType.OKTA.providerAccountLabel());
        for (OidcProviderType kind : List.of(OidcProviderType.GITHUB, OidcProviderType.KEYCLOAK, OidcProviderType.OIDC,
                                             OidcProviderType.AUTH0, OidcProviderType.AMAZON_COGNITO)) {
            assertFalse(kind.offeredToOrganizations(), kind.key());
            assertThrows(IllegalArgumentException.class, () -> kind.authority("acme"), kind.key());
        }
    }
}
