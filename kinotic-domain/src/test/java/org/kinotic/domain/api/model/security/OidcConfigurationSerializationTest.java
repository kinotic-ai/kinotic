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
 * through the base class — the shape every repository read depends on — for each managed kind and the
 * organization's own kinds, and that an organization's configuration carries no field naming a secret.
 */
class OidcConfigurationSerializationTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void orgSignupConfigurationRoundTripsThroughBaseClassWithTypeDiscriminator() {
        OrgSignupOidcConfiguration social = new OrgSignupOidcConfiguration();
        social.setSecretNameRef("github-platform")
              .setTokenUri("https://github.com/login/oauth/access_token")
              .setName("GitHub")
                .setProvider(OidcProviderKind.GITHUB)
                .setClientId("client")
                .setId("github-platform");

        String json = jsonMapper.writeValueAsString(social);
        assertTrue(json.contains("\"type\":\"ORG_SIGNUP\""), "document must carry the discriminator: " + json);

        OidcConfiguration read = jsonMapper.readValue(json, OidcConfiguration.class);
        OrgSignupOidcConfiguration readSocial = assertInstanceOf(OrgSignupOidcConfiguration.class, read);
        assertEquals("github-platform", readSocial.getSecretNameRef());
        assertEquals("https://github.com/login/oauth/access_token", readSocial.getTokenUri());
        assertEquals(OidcProviderKind.GITHUB, readSocial.getProvider());
    }

    @Test
    void applicationSocialConfigurationRoundTripsThroughBaseClassWithTypeDiscriminator() {
        ApplicationSocialOidcConfiguration social = new ApplicationSocialOidcConfiguration();
        social.setSecretNameRef("google-platform")
              .setName("Google")
              .setProvider(OidcProviderKind.GOOGLE)
              .setClientId("client")
              .setAuthority("https://accounts.google.com")
              .setId("google-platform");

        String json = jsonMapper.writeValueAsString(social);
        assertTrue(json.contains("\"type\":\"APP_SOCIAL\""), "document must carry the discriminator: " + json);

        OidcConfiguration read = jsonMapper.readValue(json, OidcConfiguration.class);
        ApplicationSocialOidcConfiguration readSocial = assertInstanceOf(ApplicationSocialOidcConfiguration.class, read);
        assertEquals("google-platform", readSocial.getSecretNameRef());
        assertEquals(OidcProviderKind.GOOGLE, readSocial.getProvider());
    }

    @Test
    void organizationConfigurationRoundTripsWithoutNamingASecret() {
        OrganizationOidcConfiguration owned = new OrganizationOidcConfiguration();
        owned.setOrganizationId("acme");
        owned.setProviderAccount("acme.okta.com");
        owned.setName("Acme Okta")
             .setProvider(OidcProviderKind.OKTA)
             .setClientId("client")
             .setId("config-1");

        String json = jsonMapper.writeValueAsString(owned);
        assertTrue(json.contains("\"type\":\"ORGANIZATION\""), "document must carry the discriminator: " + json);
        assertFalse(json.toLowerCase(Locale.ROOT).contains("secret"), "an organization's row names no secret: " + json);

        OidcConfiguration read = jsonMapper.readValue(json, OidcConfiguration.class);
        OrganizationOidcConfiguration readOwned = assertInstanceOf(OrganizationOidcConfiguration.class, read);
        assertEquals("acme", readOwned.getOrganizationId());
        assertEquals("acme.okta.com", readOwned.getProviderAccount());
    }

    @Test
    void applicationConfigurationRoundTripsWithItsScope() {
        ApplicationOidcConfiguration owned = new ApplicationOidcConfiguration();
        owned.setApplicationId("orders");
        owned.setOrganizationId("acme")
             .setProviderAccount("9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718")
             .setCreateUserOnSignIn(true);
        owned.setName("Acme Entra")
             .setProvider(OidcProviderKind.AZURE_AD)
             .setClientId("client")
             .setId("config-2");

        String json = jsonMapper.writeValueAsString(owned);
        assertTrue(json.contains("\"type\":\"APPLICATION\""), "document must carry the discriminator: " + json);
        assertFalse(json.toLowerCase(Locale.ROOT).contains("secret"), "an organization's row names no secret: " + json);

        OidcConfiguration read = jsonMapper.readValue(json, OidcConfiguration.class);
        ApplicationOidcConfiguration readOwned = assertInstanceOf(ApplicationOidcConfiguration.class, read);
        assertEquals("acme", readOwned.getOrganizationId());
        assertEquals("orders", readOwned.getApplicationId());
        assertTrue(readOwned.isCreateUserOnSignIn());
    }

    @Test
    void tenantConfigurationRoundTripsWithItsScope() {
        TenantOidcConfiguration owned = new TenantOidcConfiguration();
        owned.setApplicationId("orders")
             .setTenantId("globex");
        owned.setOrganizationId("acme")
             .setProviderAccount("globex.okta.com");
        owned.setName("Globex Okta")
             .setProvider(OidcProviderKind.OKTA)
             .setClientId("client")
             .setId("config-3");

        String json = jsonMapper.writeValueAsString(owned);
        assertTrue(json.contains("\"type\":\"TENANT\""), "document must carry the discriminator: " + json);

        OidcConfiguration read = jsonMapper.readValue(json, OidcConfiguration.class);
        TenantOidcConfiguration readOwned = assertInstanceOf(TenantOidcConfiguration.class, read);
        assertEquals("acme", readOwned.getOrganizationId());
        assertEquals("orders", readOwned.getApplicationId());
        assertEquals("globex", readOwned.getTenantId());
    }
}
