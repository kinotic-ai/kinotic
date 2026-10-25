package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.ClientRegistrationType;
import org.kinotic.domain.api.model.security.ManagedSignIn;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultSystemParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.TenantService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies where an organization's OIDC configurations are offered: one made for the organization's own login
 * is offered there, after the org sign-up providers, and at none of its applications; one made for an
 * application is offered at that application only and must name one of the organization's; a disabled
 * configuration is offered nowhere; an application's configuration may sign in through Kinotic's registration of
 * its provider kind, which is offered at the application's login only while the system's registration is enabled,
 * and switch to the organization's own and back as one configuration; the system holds one enabled registration of
 * each kind; one made for a tenant of an application is offered at that tenant's login, which also offers the
 * application's, and nowhere else; and a copy of a configuration into another scope is a configuration of that
 * scope, with the source's secret and an id of its own.
 */
@SpringBootTest
public class OidcConfigurationScopeTests extends KinoticTestBase {

    @Autowired
    private OidcConfigurationService oidcConfigurations;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private OrganizationService organizations;

    // the configurations a test saves stay enabled, so each test configures an organization of its own, as its administrator
    private Organization organization;
    private OrganizationParticipant administrator;

    @BeforeEach
    public void createOrganization() throws Exception {
        organization = await(organizations.create(new Organization().setName("Configuration scopes " + suffix())
                                                                    .setDescription("Organization of one configuration scope test")));
        administrator = new DefaultOrganizationParticipant("scope-admin-" + suffix(), organization.getId(),
                                                           Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                                                                  "displayName", "Scope Admin"),
                                                           List.of("ADMIN"));
    }

    private <T> Future<T> asAdministrator(Supplier<Future<T>> call) {
        return runAs(administrator, call);
    }

    // the system's registrations are saved by a system participant only
    private <T> Future<T> asSystem(Supplier<Future<T>> call) {
        return runAs(new DefaultSystemParticipant("scope-system-" + suffix(), Map.of(), List.of()), call);
    }

    @Test
    public void aConfigurationIsOfferedAtTheScopeItWasMadeFor() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        Application application = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Scope application", null)));
        OrganizationOidcConfiguration portalOkta = await(asAdministrator(() -> oidcConfigurations.save(forOrganization("Acme Okta"), null)));
        ApplicationOidcConfiguration appOkta = await(asAdministrator(() -> oidcConfigurations.save(forApplication("Orders Okta", appId), null)));
        // the offered lists are searches, which see a write only once the index has refreshed
        await(oidcConfigurations.syncIndex());

        assertEquals(portalOkta.getId(), await(oidcConfigurations.findOffered(portalOkta.getId(), organization.getId(), null)).getId());
        assertNull(await(oidcConfigurations.findOffered(portalOkta.getId(), organization.getId(), appId)));
        assertEquals(appOkta.getId(), await(oidcConfigurations.findOffered(appOkta.getId(), organization.getId(), appId)).getId());
        assertNull(await(oidcConfigurations.findOffered(appOkta.getId(), organization.getId(), null)));
        assertEquals(List.of(appOkta.getId()), ids(await(oidcConfigurations.findEnabledForScope(organization.getId(), appId))));

        // the organization's own login offers the org sign-up providers first, then its own
        List<OidcConfiguration> atOrganization = await(oidcConfigurations.findEnabledForScope(organization.getId(), null));
        int social = (int) atOrganization.stream().filter(OrgSignupOidcConfiguration.class::isInstance).count();
        assertTrue(social > 0, "the org sign-up providers are offered at the organization's login");
        assertTrue(atOrganization.subList(0, social).stream().allMatch(OrgSignupOidcConfiguration.class::isInstance));
        assertTrue(ids(atOrganization).contains(portalOkta.getId()));
        assertFalse(ids(atOrganization).contains(appOkta.getId()));

        // another application of the organization does not offer it, and another organization owns neither
        String otherAppId = "oidc-scope-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(otherAppId, "Other application", null)));
        assertNull(await(oidcConfigurations.findOffered(appOkta.getId(), organization.getId(), otherAppId)));
        assertNull(await(oidcConfigurations.findOffered(appOkta.getId(), "globex", appId)));
        assertNull(await(oidcConfigurations.findOwnedById(appOkta.getId(), "globex")));

        // a disabled configuration is offered nowhere
        await(asAdministrator(() -> oidcConfigurations.saveSync(appOkta.setEnabled(false))));
        assertNull(await(oidcConfigurations.findOffered(appOkta.getId(), organization.getId(), appId)));
        assertTrue(await(oidcConfigurations.findEnabledForScope(organization.getId(), appId)).isEmpty());
        assertEquals(application.getId(), appId);
    }

    @Test
    public void anOrgSignupRegistrationIsOfferedAtEveryOrganizationsLoginOnly() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Sign-up application", null)));
        OrgSignupOidcConfiguration github = await(oidcConfigurations.findEnabledOrgSignupProviders()).getFirst();

        assertEquals(github.getId(), await(oidcConfigurations.findOffered(github.getId(), organization.getId(), null)).getId());
        assertEquals(github.getId(), await(oidcConfigurations.findOffered(github.getId(), "globex", null)).getId());
        assertNull(await(oidcConfigurations.findOffered(github.getId(), organization.getId(), appId)));
    }

    @Test
    public void aTenantConfigurationIsOfferedAtTheTenantsLoginWithTheApplications() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Tenant application", null)));
        Tenant globex = await(asAdministrator(() -> tenantService.createTenantIfNotExist(appId, "Globex", "A customer")));
        assertEquals("globex", globex.getId());
        ApplicationOidcConfiguration appOkta = await(asAdministrator(() -> oidcConfigurations.save(forApplication("Orders Okta", appId), null)));
        TenantOidcConfiguration tenantOkta = await(asAdministrator(() -> oidcConfigurations.save(forTenant("Globex Okta", appId, globex.getId()), null)));
        await(oidcConfigurations.syncIndex());

        // the tenant's login offers the tenant's own configuration first, then the application's
        assertEquals(List.of(tenantOkta.getId(), appOkta.getId()), ids(await(oidcConfigurations.findEnabledForScope(organization.getId(), appId, globex.getId()))));
        assertEquals(tenantOkta.getId(), await(oidcConfigurations.findOffered(tenantOkta.getId(), organization.getId(), appId, globex.getId())).getId());
        assertEquals(appOkta.getId(), await(oidcConfigurations.findOffered(appOkta.getId(), organization.getId(), appId, globex.getId())).getId());
        // the application's own login and its other tenants do not offer it
        assertEquals(List.of(appOkta.getId()), ids(await(oidcConfigurations.findEnabledForScope(organization.getId(), appId))));
        assertNull(await(oidcConfigurations.findOffered(tenantOkta.getId(), organization.getId(), appId, null)));
        assertNull(await(oidcConfigurations.findOffered(tenantOkta.getId(), organization.getId(), appId, "initech")));

        // a tenant configuration must name a tenant of the application
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> oidcConfigurations.save(forTenant("Nowhere Okta", appId, "no-such-tenant"), null)));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> oidcConfigurations.save(forTenant("Nowhere Okta", appId, null), null)));
    }

    @Test
    public void anApplicationConfigurationMustNameOneOfTheOrganizationsApplications() throws Exception {
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> oidcConfigurations.save(forApplication("Nowhere Okta", "no-such-application-" + suffix()), null)));
        assertInstanceOf(IllegalArgumentException.class,
                         failure(() -> oidcConfigurations.save(forApplication("Nowhere Okta", null), null)));
    }

    @Test
    public void anApplicationsManagedConfigurationSignsInThroughKinoticsRegistration() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Managed application", null)));
        // the system's registration is never offered on its own, only through an application's configuration
        assertNull(await(oidcConfigurations.findOffered("google-platform", organization.getId(), appId)));
        assertNull(await(oidcConfigurations.findOfferedManaged("google-platform", organization.getId(), appId)));

        ApplicationOidcConfiguration saved = await(asAdministrator(() -> oidcConfigurations.save(managedGoogle(appId), null)));
        assertNull(saved.getAuthority());
        assertFalse(saved.isConfidentialClient());
        await(oidcConfigurations.syncIndex());
        assertEquals(saved.getId(), await(oidcConfigurations.findOffered(saved.getId(), organization.getId(), appId)).getId());
        assertEquals(List.of(saved.getId()), ids(await(oidcConfigurations.findEnabledForScope(organization.getId(), appId))));
        ManagedSignIn signIn = await(oidcConfigurations.findOfferedManaged(saved.getId(), organization.getId(), appId));
        assertEquals(saved.getId(), signIn.configuration().getId());
        assertEquals("google-platform", signIn.registration().getId());

        // the organization's own login and its other applications do not offer it
        assertNull(await(oidcConfigurations.findOffered(saved.getId(), organization.getId(), null)));
        assertFalse(ids(await(oidcConfigurations.findEnabledForScope(organization.getId(), null))).contains(saved.getId()));
        assertNull(await(oidcConfigurations.findOfferedManaged(saved.getId(), organization.getId(), "another-app-" + suffix())));

        // switched to the application's own registration it is the same configuration, its credentials checked from then on
        saved.setClientRegistrationType(ClientRegistrationType.OWN)
             .setClientId("orders-google-client");
        ApplicationOidcConfiguration own = await(asAdministrator(() -> oidcConfigurations.save(saved, "orders-google-secret")));
        assertEquals(saved.getId(), own.getId());
        assertEquals("https://accounts.google.com", own.getAuthority());
        assertTrue(own.isConfidentialClient());
        assertNull(await(oidcConfigurations.findOfferedManaged(own.getId(), organization.getId(), appId)));

        // and switched back it keeps them for the next switch, and has none of its own to copy
        own.setClientRegistrationType(ClientRegistrationType.MANAGED);
        ApplicationOidcConfiguration managed = await(asAdministrator(() -> oidcConfigurations.save(own, null)));
        assertEquals("orders-google-client", managed.getClientId());
        assertEquals("orders-google-secret", await(oidcConfigurations.findClientSecret(managed)));
        assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.copy(managed.getId(), null)));
        // its users are bound to identities Google asserted, so it keeps signing in with Google
        managed.setProvider(OidcProviderType.AZURE_AD);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.save(managed, null)));

        // a kind Kinotic holds no registration for is refused
        ApplicationOidcConfiguration okta = forApplication("Orders Okta", appId).setClientRegistrationType(ClientRegistrationType.MANAGED);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.save(okta, null)));
    }

    @Test
    public void aManagedConfigurationIsOfferedOnlyWhileKinoticsRegistrationIsEnabled() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Unmanaged application", null)));
        ApplicationOidcConfiguration saved = await(asAdministrator(() -> oidcConfigurations.save(managedGoogle(appId), "kept-google-secret")));
        ApplicationSocialOidcConfiguration registration = (ApplicationSocialOidcConfiguration) await(oidcConfigurations.findById("google-platform"));
        await(asSystem(() -> oidcConfigurations.saveSync(registration.setEnabled(false))));
        try {
            await(oidcConfigurations.syncIndex());
            assertNull(await(oidcConfigurations.findOffered(saved.getId(), organization.getId(), appId)));
            assertTrue(await(oidcConfigurations.findEnabledForScope(organization.getId(), appId)).isEmpty());
            assertNull(await(oidcConfigurations.findOfferedManaged(saved.getId(), organization.getId(), appId)));

            // its organization still edits the configuration it has, and only one starting to use Kinotic's is refused
            saved.setEnabled(false);
            assertFalse(await(asAdministrator(() -> oidcConfigurations.save(saved, null))).isEnabled());
            assertFalse(await(asAdministrator(() -> oidcConfigurations.deleteClientSecret(saved.getId()))).isConfidentialClient());
            assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.save(managedGoogle(appId), null)));
        } finally {
            await(asSystem(() -> oidcConfigurations.saveSync(registration.setEnabled(true))));
        }
    }

    @Test
    public void theSystemHoldsOneEnabledRegistrationOfEachKind() throws Exception {
        ApplicationSocialOidcConfiguration second = new ApplicationSocialOidcConfiguration();
        second.setName("Second Google")
              .setProvider(OidcProviderType.GOOGLE)
              .setClientId("second-google-client")
              .setEnabled(true);
        assertInstanceOf(IllegalArgumentException.class,
                         assertThrows(ExecutionException.class, () -> await(asSystem(() -> oidcConfigurations.save(second)))).getCause());

        // the enabled one saves as itself
        OidcConfiguration google = await(oidcConfigurations.findById("google-platform"));
        assertEquals(google.getId(), await(asSystem(() -> oidcConfigurations.saveSync(google))).getId());
    }

    @Test
    public void aCopyIsAConfigurationOfTheTargetScopeWithTheSourcesSecret() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Copy application", null)));
        OrganizationOidcConfiguration source = await(asAdministrator(() -> oidcConfigurations.save(forOrganization("Acme Okta"), "acme-secret")));

        OwnedOidcConfiguration copy = await(asAdministrator(() -> oidcConfigurations.copy(source.getId(), appId)));
        ApplicationOidcConfiguration forApp = assertInstanceOf(ApplicationOidcConfiguration.class, copy);
        assertNotEquals(source.getId(), copy.getId());
        assertEquals(appId, forApp.getApplicationId());
        assertEquals(source.getProvider(), copy.getProvider());
        assertEquals(source.getProviderAccount(), copy.getProviderAccount());
        assertEquals(source.getClientId(), copy.getClientId());
        assertEquals(source.getAuthority(), copy.getAuthority());
        assertTrue(copy.isConfidentialClient());
        assertFalse(copy.isCreateUserOnSignIn());
        assertEquals("acme-secret", await(oidcConfigurations.findClientSecret(copy)));
        await(oidcConfigurations.syncIndex());
        assertEquals(copy.getId(), await(oidcConfigurations.findOffered(copy.getId(), organization.getId(), appId)).getId());

        // the copies are independent: a new secret on the source leaves the copy's as it was
        await(asAdministrator(() -> oidcConfigurations.save(source, "acme-secret-2")));
        assertEquals("acme-secret", await(oidcConfigurations.findClientSecret(copy)));

        // a copy of a configuration the organization does not own, or into an application it does not have, is refused
        assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.copy(UUID.randomUUID().toString(), appId)));
        assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.copy(source.getId(), "no-such-application-" + suffix())));
    }

    private OrganizationOidcConfiguration forOrganization(String name) {
        OrganizationOidcConfiguration ret = new OrganizationOidcConfiguration();
        okta(ret, name);
        return ret;
    }

    private ApplicationOidcConfiguration managedGoogle(String applicationId) {
        ApplicationOidcConfiguration ret = new ApplicationOidcConfiguration().setApplicationId(applicationId)
                                                                             .setClientRegistrationType(ClientRegistrationType.MANAGED);
        ret.setOrganizationId(organization.getId());
        ret.setName("Google")
           .setProvider(OidcProviderType.GOOGLE)
           .setEnabled(true);
        return ret;
    }

    private ApplicationOidcConfiguration forApplication(String name, String applicationId) {
        ApplicationOidcConfiguration ret = new ApplicationOidcConfiguration();
        ret.setApplicationId(applicationId);
        okta(ret, name);
        return ret;
    }

    private TenantOidcConfiguration forTenant(String name, String applicationId, String tenantId) {
        TenantOidcConfiguration ret = new TenantOidcConfiguration();
        ret.setApplicationId(applicationId)
           .setTenantId(tenantId);
        okta(ret, name);
        return ret;
    }

    private void okta(OwnedOidcConfiguration configuration, String name) {
        configuration.setOrganizationId(organization.getId())
                     .setProviderAccount("acme.okta.com");
        configuration.setName(name)
                     .setProvider(OidcProviderType.OKTA)
                     .setClientId("acme-client")
                     .setEnabled(true);
    }

    private static List<String> ids(List<OidcConfiguration> configurations) {
        return configurations.stream().map(OidcConfiguration::getId).toList();
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(asAdministrator(call::get))).getCause();
    }
}
