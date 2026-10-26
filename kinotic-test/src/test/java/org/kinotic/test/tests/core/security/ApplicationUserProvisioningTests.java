package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.exceptions.UserDisabledException;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ApplicationSignUpService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.management.api.model.AddApplicationUserRequest;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.TenantService;
import org.kinotic.management.api.services.security.MemberService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how an organization's administrators add people to its applications and how those people first sign in:
 * an address becomes a pending user of the application at once, with or without an invitation; the first sign-in
 * through one of the application's providers under that address claims the user, at the application's login or its
 * tenant's, and no one else's; an invitation claims the user with a password; a user always joins one of the
 * application's tenants where its administrators manage them, and gets one of its own where the system does; an
 * application's login must offer a way
 * to sign in before anyone is added; and removing a pending user removes its invitation.
 */
@SpringBootTest
public class ApplicationUserProvisioningTests extends KinoticTestBase {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Autowired
    private MemberService members;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ApplicationSignUpService signUps;

    @Autowired
    private InviteService invites;

    @Autowired
    private OidcConfigurationService oidcConfigurations;

    @Autowired
    private PasswordConfigurationService passwordConfigurations;

    @Autowired
    private ParticipantIdentityService identities;

    @Autowired
    private OrganizationService organizations;

    private Organization organization;
    private OrganizationParticipant administrator;

    @BeforeEach
    public void createOrganization() throws Exception {
        organization = await(organizations.create(new Organization().setName("Application users " + suffix())
                                                                    .setDescription("Organization of one application user test")));
        administrator = new DefaultOrganizationParticipant("app-user-admin-" + suffix(), organization.getId(),
                                                           Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                                                                  "displayName", "Application Admin"),
                                                           List.of("ADMIN"));
    }

    private <T> Future<T> asAdministrator(Supplier<Future<T>> call) {
        return runAs(administrator, call);
    }

    @Test
    public void aUserAddedWithoutAnInvitationIsClaimedByTheApplicationsProviderOnce() throws Exception {
        String appId = "claimed-app-" + suffix();
        Application application = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Claimed application", TenantManagement.SYSTEM)));
        // a provider that signs no one up still signs in the people the administrators added
        ApplicationOidcConfiguration google = await(asAdministrator(() -> oidcConfigurations.save(google(new ApplicationOidcConfiguration().setApplicationId(appId)), null)));
        await(oidcConfigurations.syncIndex());

        String subject = "claims-" + suffix();
        String email = subject + "@example.test";
        ApplicationParticipantIdentity added = await(asAdministrator(() -> members.addApplicationUser(request(email, "Pat Pending", appId, null, false))));
        assertTrue(added.isPending());
        assertNotNull(added.getTenantId(), "the system gives the user a tenant of its own");
        assertNull(await(invites.findPendingInvite(email, organization.getId(), appId)));
        assertTrue(listed(appId, email).isPending());

        await(identities.syncIndex());
        LoginScope scope = new LoginScope(application, null);
        ApplicationParticipantIdentity claimed = await(signUps.findOrCreateOidcUser(scope, google, googleClaims(subject)));
        assertEquals(added.getId(), claimed.getId());
        assertFalse(listed(appId, email).isPending());
        // the identity now signs in as the user it claimed
        assertEquals(added.getId(), await(signUps.findOrCreateOidcUser(scope, google, googleClaims(subject))).getId());

        // once claimed, another identity under the same email takes no one
        assertNull(await(signUps.findOrCreateOidcUser(scope, google, Map.of("sub", "other-" + suffix(), "email", email, "email_verified", true))));
    }

    @Test
    public void aProviderClaimSpendsTheInvitation() throws Exception {
        String appId = "spent-app-" + suffix();
        Application application = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Spent application", TenantManagement.SYSTEM)));
        ApplicationOidcConfiguration google = await(asAdministrator(() -> oidcConfigurations.save(google(new ApplicationOidcConfiguration().setApplicationId(appId)), null)));
        // the application has no primary UI to sign in at, so its invitation is accepted with a password
        await(asAdministrator(() -> passwordConfigurations.save(password(appId, null))));
        await(oidcConfigurations.syncIndex());

        String subject = "spends-" + suffix();
        String email = subject + "@example.test";
        await(asAdministrator(() -> members.addApplicationUser(request(email, null, appId, null, true))));
        assertNotNull(await(invites.findPendingInvite(email, organization.getId(), appId)));

        await(identities.syncIndex());
        assertNotNull(await(signUps.findOrCreateOidcUser(new LoginScope(application, null), google, googleClaims(subject))));
        // the claim spends the invitation, and someone signed in is sent no other
        assertNull(await(invites.findPendingInvite(email, organization.getId(), appId)));
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.sendInvitation(email, appId)));
    }

    @Test
    public void aDisabledUserIsNotClaimed() throws Exception {
        String appId = "disabled-app-" + suffix();
        Application application = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Disabled application", TenantManagement.SYSTEM)));
        ApplicationOidcConfiguration google = await(asAdministrator(() -> oidcConfigurations.save(google(new ApplicationOidcConfiguration().setApplicationId(appId)), null)));
        await(oidcConfigurations.syncIndex());

        String subject = "disabled-" + suffix();
        ApplicationParticipantIdentity added = await(asAdministrator(() -> members.addApplicationUser(request(subject + "@example.test", null, appId, null, false))));
        await(asAdministrator(() -> members.setMemberEnabled(added.getId(), false)));
        await(identities.syncIndex());

        assertInstanceOf(UserDisabledException.class, failure(() -> signUps.findOrCreateOidcUser(new LoginScope(application, null), google, googleClaims(subject))));
        assertTrue(listed(appId, subject + "@example.test").isPending());
    }

    @Test
    public void aUserAddedWithAnInvitationClaimsTheUserByAcceptingWithAPassword() throws Exception {
        String appId = "invited-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Invited application", TenantManagement.SYSTEM)));
        await(asAdministrator(() -> passwordConfigurations.save(password(appId, null))));

        String email = "accepts-" + suffix() + "@example.test";
        ApplicationParticipantIdentity added = await(asAdministrator(() -> members.addApplicationUser(request(email, "Pat Accepts", appId, null, true))));
        PendingInvite invite = await(invites.findPendingInvite(email, organization.getId(), appId));
        assertNotNull(invite);
        assertEquals(added.getTenantId(), invite.getTenantId());
        // sent again, the same invitation goes out
        await(asAdministrator(() -> members.sendInvitation(email, appId)));

        await(identities.syncIndex());
        Participant user = await(invites.acceptLocalInvite(invite.getVerificationToken(), PASSWORD, "Pat Herself"));
        assertEquals(added.getId(), user.getId());
        assertEquals(appId, assertInstanceOf(ApplicationParticipant.class, user).getApplicationId());
        assertTrue(await(identities.hasPassword(added.getId())));
        assertEquals("Pat Herself", listed(appId, email).getDisplayName());
        assertFalse(listed(appId, email).isPending());
    }

    @Test
    public void aTenantsUserIsAddedOnlyToATenantTheApplicationHasAndClaimedAtItsLogin() throws Exception {
        String appId = "tenanted-app-" + suffix();
        Application application = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Tenanted application", TenantManagement.TENANT_ADMINISTRATORS)));
        Tenant globex = await(asAdministrator(() -> tenantService.createTenantIfNotExist(appId, "Globex", "A customer")));
        Tenant initech = await(asAdministrator(() -> tenantService.createTenantIfNotExist(appId, "Initech", "Another customer")));
        // the tenant's own provider, as a tenant's SSO
        TenantOidcConfiguration okta = await(asAdministrator(() -> oidcConfigurations.save(okta(new TenantOidcConfiguration().setApplicationId(appId)
                                                                                                                           .setTenantId(globex.getId())), null)));
        await(oidcConfigurations.syncIndex());

        // every user of the application belongs to one of its tenants
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.addApplicationUser(request("untenanted-" + suffix() + "@example.test", null, appId, null, false))));
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.addApplicationUser(request("nowhere-" + suffix() + "@example.test", null, appId, "no-such-tenant", false))));

        // an invitation links to the primary UI, whose login offers neither the tenant's provider nor a password here
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.addApplicationUser(request("invited-" + suffix() + "@example.test", null, appId, globex.getId(), true))));

        // a person not invited signs in at the tenant's own login, which offers its provider, and is sent no invitation later
        String subject = "globex-" + suffix();
        ApplicationParticipantIdentity added = await(asAdministrator(() -> members.addApplicationUser(request(subject + "@example.test", null, appId, globex.getId(), false))));
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.sendInvitation(subject + "@example.test", appId)));
        assertEquals(globex.getId(), added.getTenantId());
        await(identities.syncIndex());

        // another tenant's login admits none of Globex's users
        assertNull(await(signUps.findOrCreateOidcUser(new LoginScope(application, initech.getId()), okta, googleClaims(subject))));
        ApplicationParticipantIdentity claimed = await(signUps.findOrCreateOidcUser(new LoginScope(application, globex.getId()), okta, googleClaims(subject)));
        assertEquals(added.getId(), claimed.getId());
        assertEquals(globex.getId(), claimed.getTenantId());
    }

    @Test
    public void anApplicationWhoseTenantsTheSystemManagesTakesNoTenant() throws Exception {
        String appId = "system-tenants-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "System tenants application", TenantManagement.SYSTEM)));
        await(asAdministrator(() -> oidcConfigurations.save(google(new ApplicationOidcConfiguration().setApplicationId(appId)), null)));
        await(oidcConfigurations.syncIndex());

        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.addApplicationUser(request("named-" + suffix() + "@example.test", null, appId, "a-tenant", false))));
    }

    @Test
    public void aUserIsAddedOnlyWhileTheApplicationsLoginOffersAWayIn() throws Exception {
        String appId = "closed-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Closed application", TenantManagement.SYSTEM)));

        // with no provider and no password, neither an invitation nor a sign-in could claim the user
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.addApplicationUser(request("closed-" + suffix() + "@example.test", null, appId, null, true))));
        // a provider alone lets a person not invited claim the user, but an invitation links to a primary UI the
        // application does not have
        await(asAdministrator(() -> oidcConfigurations.save(google(new ApplicationOidcConfiguration().setApplicationId(appId)), null)));
        await(oidcConfigurations.syncIndex());
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.addApplicationUser(request("no-ui-" + suffix() + "@example.test", null, appId, null, true))));
        String quiet = "quiet-" + suffix() + "@example.test";
        assertTrue(await(asAdministrator(() -> members.addApplicationUser(request(quiet, null, appId, null, false)))).isPending());
        // nor is an invitation sent to that person later
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.sendInvitation(quiet, appId)));
        // a password lets an invitation claim them
        await(asAdministrator(() -> passwordConfigurations.save(password(appId, null))));
        assertTrue(await(asAdministrator(() -> members.addApplicationUser(request("invited-" + suffix() + "@example.test", null, appId, null, true)))).isPending());
        // an application of another organization is not this one's to add to
        assertInstanceOf(IllegalArgumentException.class, failure(() -> members.addApplicationUser(request("foreign-" + suffix() + "@example.test", null, "no-such-app-" + suffix(), null, true))));
    }

    @Test
    public void removingAPendingUserRemovesItsInvitation() throws Exception {
        String appId = "removed-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Removed application", TenantManagement.SYSTEM)));
        await(asAdministrator(() -> passwordConfigurations.save(password(appId, null))));

        String email = "removed-" + suffix() + "@example.test";
        ApplicationParticipantIdentity added = await(asAdministrator(() -> members.addApplicationUser(request(email, null, appId, null, true))));
        assertNotNull(await(invites.findPendingInvite(email, organization.getId(), appId)));

        await(asAdministrator(() -> members.removeMember(added.getId())));
        assertNull(await(identities.findById(added.getId())));
        assertNull(await(invites.findPendingInvite(email, organization.getId(), appId)));
    }

    private static AddApplicationUserRequest request(String email, String displayName, String applicationId, String tenantId, boolean sendInvitation) {
        return new AddApplicationUserRequest().setEmail(email)
                                              .setDisplayName(displayName)
                                              .setApplicationId(applicationId)
                                              .setTenantId(tenantId)
                                              .setSendInvitation(sendInvitation);
    }

    // the user at email as the application's Users page lists them
    private ApplicationParticipantIdentity listed(String applicationId, String email) throws Exception {
        await(identities.syncIndex());
        Page<ApplicationParticipantIdentity> page = await(asAdministrator(() -> members.findApplicationUsers(applicationId, Pageable.ofSize(1000))));
        return page.getContent().stream().filter(user -> email.equals(user.getEmail())).findFirst().orElseThrow();
    }

    private PasswordConfiguration password(String applicationId, String tenantId) {
        return new PasswordConfiguration().setOrganizationId(organization.getId())
                                          .setApplicationId(applicationId)
                                          .setTenantId(tenantId)
                                          .setEnabled(true);
    }

    private <C extends OwnedOidcConfiguration> C google(C configuration) {
        configuration.setOrganizationId(organization.getId());
        configuration.setName("Google")
                     .setProvider(OidcProviderType.GOOGLE)
                     .setClientId("app-user-google-client")
                     .setEnabled(true);
        return configuration;
    }

    private <C extends OwnedOidcConfiguration> C okta(C configuration) {
        configuration.setOrganizationId(organization.getId())
                     .setProviderAccount("globex.okta.com");
        configuration.setName("Globex Okta")
                     .setProvider(OidcProviderType.OKTA)
                     .setClientId("globex-okta-client")
                     .setEnabled(true);
        return configuration;
    }

    private static Map<String, Object> googleClaims(String sub) {
        return Map.of("sub", sub, "email", sub + "@example.test", "email_verified", true, "name", "Pat Google");
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(asAdministrator(call::get))).getCause();
    }
}
