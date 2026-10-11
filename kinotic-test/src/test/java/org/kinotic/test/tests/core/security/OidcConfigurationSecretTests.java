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

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies where an organization's OIDC configuration keeps its client secret: given at save, it is read
 * back for that configuration alone, under its organization, and never appears on the configuration a
 * read returns; a save without a secret keeps it, a new secret replaces it, null makes the configuration a
 * public client, a public client can be given a secret again, and deleting the configuration deletes it.
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
        assertEquals("acme-secret-1", await(oidcConfigurations.findClientSecret(saved)));

        // what a client reads back is the row as persisted, which names nothing about the secret
        OrganizationOidcConfiguration read = await(oidcConfigurations.findById(saved.getId(), TEST_ORG_ID));
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
        await(runAsOrganization(() -> oidcConfigurations.save(read, null)));
        assertNull(await(oidcConfigurations.findClientSecret(saved)));
        await(runAsOrganization(() -> oidcConfigurations.save(read, "acme-secret-3")));
        assertEquals("acme-secret-3", await(oidcConfigurations.findClientSecret(saved)));

        // deleting the configuration deletes its secret
        await(runAsOrganization(() -> oidcConfigurations.deleteById(saved.getId())));
        assertNull(await(oidcConfigurations.findById(saved.getId(), TEST_ORG_ID)));
        assertNull(await(oidcConfigurations.findClientSecret(saved)));
    }

    private static OrganizationOidcConfiguration provider(String name) {
        OrganizationOidcConfiguration ret = new OrganizationOidcConfiguration();
        ret.setOrganizationId(TEST_ORG_ID);
        ret.setName(name)
           .setProvider(OidcProviderKind.OIDC)
           .setClientId("acme-client")
           .setAuthority("https://acme.okta.test")
           .setEnabled(true);
        return ret;
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
