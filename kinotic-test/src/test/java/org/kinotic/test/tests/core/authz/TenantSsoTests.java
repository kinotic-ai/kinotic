package org.kinotic.test.tests.core.authz;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.OnboardingMechanism;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultApplicationParticipant;
import org.kinotic.domain.api.services.TenantService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a tenant's own identity provider: its administrator configures one, which the login lookup then
 * finds by the tenant's id, reconfigures it in place, and removes it; a user the provider signs in for the
 * first time is created in the tenant with the role the tenant chose and found again on the next sign-in; an
 * email already held in the application is refused; a provider no tenant owns creates nobody; and a role no
 * grant on a tenant can name is refused.
 */
@SpringBootTest
public class TenantSsoTests extends KinoticTestBase {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ParticipantIdentityService identityService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private OidcConfigurationService oidcConfigurations;

    @Autowired
    private RelationshipService relationships;

    @Test
    public void aTenantSignsItsUsersInWithAProviderOfItsOwnThatProvisionsThem() throws Exception {
        String appId = "sso-app-" + suffix();
        await(runAsOrganization(() -> applicationService.createApplicationIfNotExist(appId, "SSO application",
                                                                                    EnumSet.of(OnboardingMechanism.TENANT_PER_USER))));
        UserParticipantIdentity admin = endUser(appId, "Tenant Admin");
        String tenantId = admin.getTenantId();
        await(relationships.bind(appId, AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.ADMIN),
                                 AuthzUtil.object(AuthzUtil.USER_TYPE, admin.getId()), AuthzUtil.object(AuthzUtil.TENANT_TYPE, tenantId)));
        Participant caller = participant(appId, tenantId, admin.getId());
        String viewer = AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.VIEWER);

        // the tenant has no provider until its administrator chooses one
        assertNull(await(runAs(caller, tenantService::getSso)));
        assertNull(await(oidcConfigurations.findTenantLoginConfig(TEST_ORG_ID, appId, tenantId)));
        Tenant configured = await(runAs(caller, () -> tenantService.configureSso(provider("Acme Okta"), viewer)));
        assertNotNull(configured.getSsoConfigId(), "the tenant names no configuration");
        assertEquals(viewer, configured.getSsoRoleId());
        OidcConfiguration sso = await(runAs(caller, tenantService::getSso));
        assertEquals("Acme Okta", sso.getName());
        assertEquals(appId, sso.getApplicationId());
        assertEquals(tenantId, sso.getTenantId());
        assertTrue(sso.isEnabled());
        assertEquals(sso.getId(), await(oidcConfigurations.findTenantLoginConfig(TEST_ORG_ID, appId, tenantId)).getId());

        // reconfiguring replaces the configuration in place; a role no grant on a tenant can name is refused
        assertInstanceOf(IllegalArgumentException.class,
                         failure(caller, () -> tenantService.configureSso(provider("Acme Okta"), AuthzUtil.roleId(AuthzUtil.APPLICATION_TYPE, AuthzUtil.ADMIN))));
        Tenant reconfigured = await(runAs(caller, () -> tenantService.configureSso(provider("Acme Okta again"), null)));
        assertEquals(sso.getId(), reconfigured.getSsoConfigId());
        assertNull(reconfigured.getSsoRoleId());
        assertEquals("Acme Okta again", await(runAs(caller, tenantService::getSso)).getName());
        await(runAs(caller, () -> tenantService.configureSso(provider("Acme Okta"), viewer)));
        OidcConfiguration config = await(runAs(caller, tenantService::getSso));

        // the provider signs a colleague in for the first time: created in the tenant and granted the role
        String email = "colleague-" + suffix() + "@acme.test";
        UserParticipantIdentity colleague = await(identityService.findOrCreateSsoUser(config, "okta-sub-1", email, "Colleague"));
        assertNotNull(colleague, "the provider's user was not created");
        assertEquals(tenantId, colleague.getTenantId());
        assertEquals(appId, colleague.getApplicationId());
        assertEquals(AuthType.OIDC, colleague.getAuthType());
        assertEquals("okta-sub-1", colleague.getOidcSubject());
        assertEquals(config.getId(), colleague.getOidcConfigId());
        assertEquals(email, colleague.getEmail());
        assertEquals("Colleague", colleague.getDisplayName());
        assertTrue(await(relationships.findGrants(appId, AuthzUtil.object(AuthzUtil.TENANT_TYPE, tenantId))).stream()
                        .anyMatch(grant -> grant.roleId().equals(viewer) && grant.subject().id().equals(colleague.getId())),
                   "the provider's user was not granted the tenant's role");
        // signed in again, the same user
        assertEquals(colleague.getId(), await(identityService.findOrCreateSsoUser(config, "okta-sub-1", email, "Colleague")).getId());
        // an email already held in the application is refused rather than taken over
        UserParticipantIdentity other = endUser(appId, "Other User");
        assertInstanceOf(AlreadyExistsException.class, failure(() -> identityService.findOrCreateSsoUser(config, "okta-sub-2", other.getEmail(), "Other")));
        // a provider no tenant owns creates nobody
        OidcConfiguration organizations = provider("Org SSO");
        organizations.setOrganizationId(TEST_ORG_ID);
        OidcConfiguration orgConfig = await(runAsOrganization(() -> oidcConfigurations.createSync(organizations)));
        assertNull(await(identityService.findOrCreateSsoUser(orgConfig, "okta-sub-3", "nobody-" + suffix() + "@acme.test", "Nobody")));

        // removing the provider deletes its configuration
        Tenant removed = await(runAs(caller, tenantService::removeSso));
        assertNull(removed.getSsoConfigId());
        assertNull(await(runAs(caller, tenantService::getSso)));
        assertNull(await(oidcConfigurations.findTenantLoginConfig(TEST_ORG_ID, appId, tenantId)));
        assertNull(await(oidcConfigurations.findById(config.getId(), TEST_ORG_ID)));
    }

    private static OidcConfiguration provider(String name) {
        OidcConfiguration ret = new OidcConfiguration();
        ret.setName(name)
           .setProvider(OidcProviderKind.OIDC)
           .setClientId("acme-client")
           .setAuthority("https://acme.okta.test");
        return ret;
    }

    private UserParticipantIdentity endUser(String applicationId, String displayName) throws Exception {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail("sso-" + suffix() + "@acme.test");
        user.setDisplayName(displayName);
        user.setOrganizationId(TEST_ORG_ID);
        user.setApplicationId(applicationId);
        return await(identityService.createUser(user, "Sso-User-1"));
    }

    private static Participant participant(String applicationId, String tenantId, String id) {
        return new DefaultApplicationParticipant(id, TEST_ORG_ID, applicationId, tenantId,
                                                 Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER),
                                                 List.of("USER"));
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(call.get())).getCause();
    }

    private Throwable failure(Participant caller, Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAs(caller, call::get))).getCause();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
