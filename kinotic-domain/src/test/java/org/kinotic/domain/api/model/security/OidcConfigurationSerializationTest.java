package org.kinotic.domain.api.model.security;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the polymorphic wire and index shape of {@link OidcConfiguration}: the {@code type}
 * discriminator each document carries, resolution back to the concrete subtype when deserializing
 * through the base class — the shape every repository read depends on — and that an organization's
 * configuration carries no field naming a secret.
 */
class OidcConfigurationSerializationTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void platformConfigurationRoundTripsThroughBaseClassWithTypeDiscriminator() {
        PlatformOidcConfiguration platform = new PlatformOidcConfiguration();
        platform.setSecretNameRef("github-platform")
                .setName("GitHub")
                .setProvider(OidcProviderKind.GITHUB)
                .setClientId("client")
                .setId("github-platform");

        String json = jsonMapper.writeValueAsString(platform);
        assertTrue(json.contains("\"type\":\"PLATFORM\""), "document must carry the discriminator: " + json);

        OidcConfiguration read = jsonMapper.readValue(json, OidcConfiguration.class);
        PlatformOidcConfiguration readPlatform = assertInstanceOf(PlatformOidcConfiguration.class, read);
        assertEquals("github-platform", readPlatform.getSecretNameRef());
        assertEquals(OidcProviderKind.GITHUB, readPlatform.getProvider());
    }

    @Test
    void organizationConfigurationRoundTripsWithoutNamingASecret() {
        OrganizationOidcConfiguration owned = new OrganizationOidcConfiguration();
        owned.setOrganizationId("acme")
             .setName("Acme Okta")
             .setProvider(OidcProviderKind.OIDC)
             .setClientId("client")
             .setId("config-1");

        String json = jsonMapper.writeValueAsString(owned);
        assertTrue(json.contains("\"type\":\"ORGANIZATION\""), "document must carry the discriminator: " + json);
        assertFalse(json.toLowerCase(Locale.ROOT).contains("secret"), "an organization's row names no secret: " + json);

        OidcConfiguration read = jsonMapper.readValue(json, OidcConfiguration.class);
        OrganizationOidcConfiguration readOwned = assertInstanceOf(OrganizationOidcConfiguration.class, read);
        assertEquals("acme", readOwned.getOrganizationId());
    }
}
