package org.kinotic.domain.api.model.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link OidcProviderType#admitsEveryDirectory}: only Entra's {@code common} and {@code organizations}
 * accounts admit every directory.
 */
class OidcProviderTypeTest {

    @Test
    void onlyEntrasCommonAndOrganizationsAdmitEveryDirectory() {
        assertTrue(OidcProviderType.AZURE_AD.admitsEveryDirectory("common"));
        assertTrue(OidcProviderType.AZURE_AD.admitsEveryDirectory("organizations"));
        assertFalse(OidcProviderType.AZURE_AD.admitsEveryDirectory("9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718"));
        assertFalse(OidcProviderType.OKTA.admitsEveryDirectory("common"));
    }
}
