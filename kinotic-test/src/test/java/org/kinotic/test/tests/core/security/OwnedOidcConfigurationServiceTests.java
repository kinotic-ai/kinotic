package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.security.OwnedOidcConfigurationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the published service an organization manages its own OIDC configurations through: a saved
 * configuration is the caller's organization's whatever organization it named, is found and listed, holds its
 * secret, is copied into an application's scope and deleted; another owner's row is neither written over nor
 * deleted by its id; and the organization's own login never takes {@code createUserOnSignIn}, since its members are
 * added by its administrators, nor a Microsoft account that admits every directory, while an application's takes it.
 */
@SpringBootTest
public class OwnedOidcConfigurationServiceTests extends KinoticTestBase {

    @Autowired
    private OwnedOidcConfigurationService ownedConfigurations;

    @Autowired
    private OidcConfigurationService oidcConfigurations;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private OrganizationService organizations;

    // the configurations a test saves stay enabled, so each test configures an organization of its own, as its administrator
    private Organization organization;
    private OrganizationParticipant administrator;

    @BeforeEach
    public void createOrganization() throws Exception {
        organization = await(organizations.create(new Organization().setName("Owned configurations " + suffix())
                                                                    .setDescription("Organization of one owned configuration test")));
        administrator = new DefaultOrganizationParticipant("owned-admin-" + suffix(), organization.getId(),
                                                           Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                                                                  "displayName", "Owned Admin"),
                                                           List.of("ADMIN"));
    }

    private <T> Future<T> asAdministrator(Supplier<Future<T>> call) {
        return runAs(administrator, call);
    }

    @Test
    public void aConfigurationIsTheCallersOrganizationsAndRoundTrips() throws Exception {
        String appId = "owned-oidc-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Owned application", null)));
        OrganizationOidcConfiguration portalOkta = okta(new OrganizationOidcConfiguration(), "Acme Okta");
        portalOkta.setOrganizationId("globex");

        OwnedOidcConfiguration saved = await(asAdministrator(() -> ownedConfigurations.save(portalOkta, "acme-secret")));
        assertNotNull(saved.getId());
        assertEquals(organization.getId(), saved.getOrganizationId());
        assertEquals("https://acme.okta.com", saved.getAuthority());
        assertTrue(saved.isConfidentialClient());
        assertEquals("acme-secret", await(oidcConfigurations.findClientSecret(saved)));
        assertEquals(saved.getId(), await(asAdministrator(() -> ownedConfigurations.findById(saved.getId()))).getId());

        // the listing is a search, which sees a write only once the index has refreshed
        await(oidcConfigurations.syncIndex());
        Page<OwnedOidcConfiguration> page = await(asAdministrator(() -> ownedConfigurations.findAll(Pageable.ofSize(100))));
        assertTrue(page.getContent().stream().anyMatch(configuration -> saved.getId().equals(configuration.getId())));
        assertTrue(page.getContent().stream().allMatch(configuration -> organization.getId().equals(configuration.getOrganizationId())));

        OwnedOidcConfiguration copy = await(asAdministrator(() -> ownedConfigurations.copy(saved.getId(), appId)));
        assertEquals(appId, assertInstanceOf(ApplicationOidcConfiguration.class, copy).getApplicationId());
        assertEquals("acme-secret", await(oidcConfigurations.findClientSecret(copy)));

        await(asAdministrator(() -> ownedConfigurations.deleteById(saved.getId())));
        assertNull(await(asAdministrator(() -> ownedConfigurations.findById(saved.getId()))));
        assertNotNull(await(asAdministrator(() -> ownedConfigurations.findById(copy.getId()))));
    }

    @Test
    public void anotherOwnersRowIsNeitherWrittenOverNorDeletedByItsId() throws Exception {
        OrgSignupOidcConfiguration github = await(oidcConfigurations.findEnabledOrgSignupProviders()).getFirst();
        OrganizationOidcConfiguration squatter = okta(new OrganizationOidcConfiguration(), "Not GitHub");
        squatter.setId(github.getId());

        assertInstanceOf(AuthorizationException.class, failure(() -> ownedConfigurations.save(squatter, null)));
        assertInstanceOf(OrgSignupOidcConfiguration.class, await(oidcConfigurations.findById(github.getId())));

        await(asAdministrator(() -> ownedConfigurations.deleteById(github.getId())));
        assertInstanceOf(OrgSignupOidcConfiguration.class, await(oidcConfigurations.findById(github.getId())));
    }

    @Test
    public void theOrganizationsLoginNeverAdmitsOnSignIn() throws Exception {
        String appId = "owned-oidc-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Google application", null)));

        OrganizationOidcConfiguration portalOkta = okta(new OrganizationOidcConfiguration(), "Acme Okta");
        portalOkta.setCreateUserOnSignIn(true);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> ownedConfigurations.save(portalOkta, null)));
        portalOkta.setCreateUserOnSignIn(false);
        assertNotNull(await(asAdministrator(() -> ownedConfigurations.save(portalOkta, null))).getId());

        // the organization's login claims members on the emails its own directory asserts, so it names one directory
        OrganizationOidcConfiguration everyDirectory = new OrganizationOidcConfiguration();
        everyDirectory.setOrganizationId(organization.getId())
                      .setProviderAccount("common");
        everyDirectory.setName("Any Microsoft account")
                      .setProvider(OidcProviderType.AZURE_AD)
                      .setClientId("acme-client")
                      .setEnabled(true);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> ownedConfigurations.save(everyDirectory, null)));

        // an application's login takes it, for any kind: with Google, sign-up with Google for the application
        ApplicationOidcConfiguration appGoogle = google(new ApplicationOidcConfiguration(), "Orders Google");
        appGoogle.setApplicationId(appId);
        appGoogle.setCreateUserOnSignIn(true);
        assertTrue(await(asAdministrator(() -> ownedConfigurations.save(appGoogle, null))).isCreateUserOnSignIn());
    }

    private <C extends OwnedOidcConfiguration> C okta(C configuration, String name) {
        configuration.setOrganizationId(organization.getId())
                     .setProviderAccount("acme.okta.com");
        configuration.setName(name)
                     .setProvider(OidcProviderType.OKTA)
                     .setClientId("acme-client")
                     .setEnabled(true);
        return configuration;
    }

    private <C extends OwnedOidcConfiguration> C google(C configuration, String name) {
        configuration.setOrganizationId(organization.getId());
        configuration.setName(name)
                     .setProvider(OidcProviderType.GOOGLE)
                     .setClientId("acme-client")
                     .setEnabled(true);
        return configuration;
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(asAdministrator(call::get))).getCause();
    }
}
