package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies where an organization's OIDC configuration keeps its client secret: given at save, it is read
 * back for that configuration alone, under its organization, and never appears on the configuration a
 * read returns; a save without a secret keeps it, a new secret replaces it, null makes the configuration a
 * public client, a public client can be given a secret again, and deleting the configuration deletes it.
 * An organization names its account at a provider offered to organizations, never a URL: the authority is
 * derived from the kind's template, and an account not in the kind's form or a kind not offered is refused.
 */
@SpringBootTest
public class OidcConfigurationSecretTests extends KinoticTestBase {

    @Autowired
    private OidcConfigurationService oidcConfigurations;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void aConfigurationsSecretIsHeldForItsOrganizationAndNeverOnTheRow() throws Exception {
        OrganizationOidcConfiguration saved = await(runAsOrganization(() -> oidcConfigurations.save(provider("Acme Okta"), "acme-secret-1")));
        assertTrue(saved.isConfidentialClient());
        assertEquals("acme-secret-1", await(oidcConfigurations.findClientSecret(saved)));

        // what a client reads back is the row as persisted, which says a secret is held and names nothing about it
        OrganizationOidcConfiguration read = (OrganizationOidcConfiguration) await(oidcConfigurations.findOwnedById(saved.getId(), TEST_ORG_ID));
        assertTrue(read.isConfidentialClient());
        String json = objectMapper.writeValueAsString(read);
        assertFalse(json.toLowerCase(Locale.ROOT).contains("secret"), json);

        // another organization's configuration of the same id reads nothing of it
        OrganizationOidcConfiguration other = new OrganizationOidcConfiguration();
        other.setOrganizationId("globex");
        other.setId(saved.getId());
        assertNull(await(oidcConfigurations.findClientSecret(other)));

        // a save of the row alone keeps the secret; a save with one replaces it
        await(runAsOrganization(() -> oidcConfigurations.save(read.setName("Acme Okta renamed"))));
        assertEquals("acme-secret-1", await(oidcConfigurations.findClientSecret(saved)));
        await(runAsOrganization(() -> oidcConfigurations.save(read, "acme-secret-2")));
        assertEquals("acme-secret-2", await(oidcConfigurations.findClientSecret(saved)));

        // null makes a public client, which can be given a secret again
        OrganizationOidcConfiguration publicClient = await(runAsOrganization(() -> oidcConfigurations.save(read, null)));
        assertFalse(publicClient.isConfidentialClient());
        assertNull(await(oidcConfigurations.findClientSecret(publicClient)));
        OrganizationOidcConfiguration confidentialAgain = await(runAsOrganization(() -> oidcConfigurations.save(read, "acme-secret-3")));
        assertTrue(confidentialAgain.isConfidentialClient());
        assertEquals("acme-secret-3", await(oidcConfigurations.findClientSecret(confidentialAgain)));

        // deleting the configuration deletes its secret
        await(runAsOrganization(() -> oidcConfigurations.deleteById(saved.getId())));
        assertNull(await(oidcConfigurations.findOwnedById(saved.getId(), TEST_ORG_ID)));
        assertNull(await(oidcConfigurations.findClientSecret(confidentialAgain)));
    }

    @Test
    public void anOrganizationNamesItsAccountAtTheProviderAndNeverAUrl() throws Exception {
        // the authority comes from the kind's template, whatever the caller sent
        OrganizationOidcConfiguration okta = await(runAsOrganization(
                () -> oidcConfigurations.save(provider("Acme Okta", config -> config.setAuthority("https://evil.example")), null)));
        assertEquals("https://acme.okta.com", okta.getAuthority());
        OrganizationOidcConfiguration entra = await(runAsOrganization(() -> oidcConfigurations.save(provider("Acme Entra", config -> {
            config.setProvider(OidcProviderKind.AZURE_AD);
            config.setProviderAccount("9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718");
        }), null)));
        assertEquals("https://login.microsoftonline.com/9f1a4c2e-3b7d-4e8f-a1b2-c3d4e5f60718/v2.0", entra.getAuthority());

        // an account not in the kind's form, or a kind no organization may configure, is refused
        for (String account : List.of("acme.okta.com/x", "evil.example", "acme.okta.com.evil.example")) {
            assertInstanceOf(IllegalArgumentException.class,
                             failure(() -> oidcConfigurations.save(provider("Acme Okta", config -> config.setProviderAccount(account)))),
                             account);
        }
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> oidcConfigurations.save(provider("Acme Okta", config -> config.setProviderAccount(null)))));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> oidcConfigurations.save(provider("Acme Keycloak", config -> config.setProvider(OidcProviderKind.KEYCLOAK)))));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> oidcConfigurations.save(provider("Anything", config -> config.setProvider(OidcProviderKind.OIDC)))));
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAsOrganization(call::get))).getCause();
    }

    private static OrganizationOidcConfiguration provider(String name, Consumer<OrganizationOidcConfiguration> change) {
        OrganizationOidcConfiguration ret = provider(name);
        change.accept(ret);
        return ret;
    }

    private static OrganizationOidcConfiguration provider(String name) {
        OrganizationOidcConfiguration ret = new OrganizationOidcConfiguration();
        ret.setOrganizationId(TEST_ORG_ID);
        ret.setProviderAccount("acme.okta.com");
        ret.setName(name)
           .setProvider(OidcProviderKind.OKTA)
           .setClientId("acme-client")
           .setEnabled(true);
        return ret;
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
