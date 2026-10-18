package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.TenantService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
 * configuration is offered nowhere; an application offers the platform's social registrations it names and no
 * other; one made for a tenant of an application is offered at that tenant's login, which also offers the
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

    @Test
    public void aConfigurationIsOfferedAtTheScopeItWasMadeFor() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Scope application", null)));
        OrganizationOidcConfiguration portalOkta = await(runAsOrganization(() -> oidcConfigurations.save(forOrganization("Acme Okta"), null)));
        ApplicationOidcConfiguration appOkta = await(runAsOrganization(() -> oidcConfigurations.save(forApplication("Orders Okta", appId), null)));
        // the offered lists are searches, which see a write only once the index has refreshed
        await(oidcConfigurations.syncIndex());

        assertEquals(portalOkta.getId(), await(oidcConfigurations.findOffered(portalOkta.getId(), TEST_ORG_ID, null)).getId());
        assertNull(await(oidcConfigurations.findOffered(portalOkta.getId(), TEST_ORG_ID, appId)));
        assertEquals(appOkta.getId(), await(oidcConfigurations.findOffered(appOkta.getId(), TEST_ORG_ID, appId)).getId());
        assertNull(await(oidcConfigurations.findOffered(appOkta.getId(), TEST_ORG_ID, null)));
        assertEquals(List.of(appOkta.getId()), ids(await(oidcConfigurations.findEnabledForScope(TEST_ORG_ID, appId))));

        // the organization's own login offers the org sign-up providers first, then its own
        List<OidcConfiguration> atOrganization = await(oidcConfigurations.findEnabledForScope(TEST_ORG_ID, null));
        int social = (int) atOrganization.stream().filter(OrgSignupOidcConfiguration.class::isInstance).count();
        assertTrue(social > 0, "the org sign-up providers are offered at the organization's login");
        assertTrue(atOrganization.subList(0, social).stream().allMatch(OrgSignupOidcConfiguration.class::isInstance));
        assertTrue(ids(atOrganization).contains(portalOkta.getId()));
        assertFalse(ids(atOrganization).contains(appOkta.getId()));

        // another application of the organization does not offer it, and another organization owns neither
        String otherAppId = "oidc-scope-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(otherAppId, "Other application", null)));
        assertNull(await(oidcConfigurations.findOffered(appOkta.getId(), TEST_ORG_ID, otherAppId)));
        assertNull(await(oidcConfigurations.findOffered(appOkta.getId(), "globex", appId)));
        assertNull(await(oidcConfigurations.findOwnedById(appOkta.getId(), "globex")));

        // a disabled configuration is offered nowhere
        await(runAsOrganization(() -> oidcConfigurations.saveSync(appOkta.setEnabled(false))));
        assertNull(await(oidcConfigurations.findOffered(appOkta.getId(), TEST_ORG_ID, appId)));
        assertTrue(await(oidcConfigurations.findEnabledForScope(TEST_ORG_ID, appId)).isEmpty());
        assertEquals(application.getId(), appId);
    }

    @Test
    public void anOrgSignupRegistrationIsOfferedAtEveryOrganizationsLoginOnly() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Sign-up application", null)));
        OrgSignupOidcConfiguration github = await(oidcConfigurations.findEnabledOrgSignupProviders()).getFirst();

        assertEquals(github.getId(), await(oidcConfigurations.findOffered(github.getId(), TEST_ORG_ID, null)).getId());
        assertEquals(github.getId(), await(oidcConfigurations.findOffered(github.getId(), "globex", null)).getId());
        assertNull(await(oidcConfigurations.findOffered(github.getId(), TEST_ORG_ID, appId)));
    }

    @Test
    public void aTenantConfigurationIsOfferedAtTheTenantsLoginWithTheApplications() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Tenant application", null)));
        Tenant globex = await(runAsOrganization(() -> tenantService.createTenantIfNotExist(appId, "Globex", "A customer")));
        assertEquals("globex", globex.getId());
        ApplicationOidcConfiguration appOkta = await(runAsOrganization(() -> oidcConfigurations.save(forApplication("Orders Okta", appId), null)));
        TenantOidcConfiguration tenantOkta = await(runAsOrganization(() -> oidcConfigurations.save(forTenant("Globex Okta", appId, globex.getId()), null)));
        await(oidcConfigurations.syncIndex());

        // the tenant's login offers the tenant's own configuration first, then the application's
        assertEquals(List.of(tenantOkta.getId(), appOkta.getId()), ids(await(oidcConfigurations.findEnabledForScope(TEST_ORG_ID, appId, globex.getId()))));
        assertEquals(tenantOkta.getId(), await(oidcConfigurations.findOffered(tenantOkta.getId(), TEST_ORG_ID, appId, globex.getId())).getId());
        assertEquals(appOkta.getId(), await(oidcConfigurations.findOffered(appOkta.getId(), TEST_ORG_ID, appId, globex.getId())).getId());
        // the application's own login and its other tenants do not offer it
        assertEquals(List.of(appOkta.getId()), ids(await(oidcConfigurations.findEnabledForScope(TEST_ORG_ID, appId))));
        assertNull(await(oidcConfigurations.findOffered(tenantOkta.getId(), TEST_ORG_ID, appId, null)));
        assertNull(await(oidcConfigurations.findOffered(tenantOkta.getId(), TEST_ORG_ID, appId, "initech")));

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
    public void anApplicationOffersThePlatformSocialRegistrationsItNames() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        Application application = await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Social application", null)));
        assertTrue(await(oidcConfigurations.findEnabledForScope(TEST_ORG_ID, appId)).isEmpty());
        // the seeded google-platform row is an ApplicationSocialOidcConfiguration
        assertNull(await(oidcConfigurations.findOffered("google-platform", TEST_ORG_ID, appId)));
        assertNull(await(oidcConfigurations.findOffered("google-platform", TEST_ORG_ID, null)));

        application.getSocialProviders().add(OidcProviderKind.GOOGLE);
        await(runAsOrganization(() -> applicationService.saveSync(application)));
        OidcConfiguration google = await(oidcConfigurations.findOffered("google-platform", TEST_ORG_ID, appId));
        assertInstanceOf(ApplicationSocialOidcConfiguration.class, google);
        assertEquals(List.of("google-platform"), ids(await(oidcConfigurations.findEnabledForScope(TEST_ORG_ID, appId))));
        // the organization's own login never offers it, and a kind the application does not name is not offered
        assertNull(await(oidcConfigurations.findOffered("google-platform", TEST_ORG_ID, null)));
        assertNull(await(oidcConfigurations.findOffered("entra-platform", TEST_ORG_ID, appId)));

        application.getSocialProviders().add(OidcProviderKind.GOOGLE);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> applicationService.saveSync(application)));
    }

    @Test
    public void aCopyIsAConfigurationOfTheTargetScopeWithTheSourcesSecret() throws Exception {
        String appId = "oidc-scope-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "Copy application", null)));
        OrganizationOidcConfiguration source = await(runAsOrganization(() -> oidcConfigurations.save(forOrganization("Acme Okta"), "acme-secret")));

        OwnedOidcConfiguration copy = await(runAsOrganization(() -> oidcConfigurations.copy(source.getId(), appId)));
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
        assertEquals(copy.getId(), await(oidcConfigurations.findOffered(copy.getId(), TEST_ORG_ID, appId)).getId());

        // the copies are independent: a new secret on the source leaves the copy's as it was
        await(runAsOrganization(() -> oidcConfigurations.save(source, "acme-secret-2")));
        assertEquals("acme-secret", await(oidcConfigurations.findClientSecret(copy)));

        // a copy of a configuration the organization does not own, or into an application it does not have, is refused
        assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.copy(UUID.randomUUID().toString(), appId)));
        assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.copy(source.getId(), "no-such-application-" + suffix())));
    }

    private static OrganizationOidcConfiguration forOrganization(String name) {
        OrganizationOidcConfiguration ret = new OrganizationOidcConfiguration();
        okta(ret, name);
        return ret;
    }

    private static ApplicationOidcConfiguration forApplication(String name, String applicationId) {
        ApplicationOidcConfiguration ret = new ApplicationOidcConfiguration();
        ret.setApplicationId(applicationId);
        okta(ret, name);
        return ret;
    }

    private static TenantOidcConfiguration forTenant(String name, String applicationId, String tenantId) {
        TenantOidcConfiguration ret = new TenantOidcConfiguration();
        ret.setApplicationId(applicationId)
           .setTenantId(tenantId);
        okta(ret, name);
        return ret;
    }

    private static void okta(OwnedOidcConfiguration configuration, String name) {
        configuration.setOrganizationId(TEST_ORG_ID)
                     .setProviderAccount("acme.okta.com");
        configuration.setName(name)
                     .setProvider(OidcProviderKind.OKTA)
                     .setClientId("acme-client")
                     .setEnabled(true);
    }

    private static List<String> ids(List<OidcConfiguration> configurations) {
        return configurations.stream().map(OidcConfiguration::getId).toList();
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAsOrganization(call::get))).getCause();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
