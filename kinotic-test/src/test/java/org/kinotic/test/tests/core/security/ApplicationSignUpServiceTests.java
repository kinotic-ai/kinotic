package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.exceptions.UnverifiedEmailException;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.ApplicationLoginMethods;
import org.kinotic.domain.api.model.security.ApplicationLoginProvider;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.ClientRegistrationType;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.PendingApplicationSignUp;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ApplicationSignUpService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.internal.api.repositories.PendingApplicationSignUpRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how people become users of an application: a user a provider signed up through Kinotic's registration
 * is the same user once the configuration switches to the application's own registration; a Microsoft email signs up
 * only when the token proves it; an application admits new
 * people at its own login only while the system manages its tenants, which gives each a tenant, and a tenant's
 * login admits them into the tenant through the tenant's own providers only; and a sign-up with an email and a
 * password creates the user once the emailed token comes back with a password, at the application it was for.
 */
@SpringBootTest
public class ApplicationSignUpServiceTests extends KinoticTestBase {

    // a work directory, whose administrator sets its users' email claims; personal accounts live in Microsoft's own
    private static final String WORK_DIRECTORY = "4a6c1d2e-8b3f-4c5d-9e7f-0a1b2c3d4e5f";
    private static final String PERSONAL_ACCOUNTS = "9188040d-6c67-4c5b-b112-36a304b66dad";

    @Autowired
    private ApplicationSignUpService signUps;

    @Autowired
    private OidcConfigurationService oidcConfigurations;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private OrganizationService organizations;

    @Autowired
    private LocalAuthenticationService localAuthentication;

    @Autowired
    private PendingApplicationSignUpRepository pendingSignUps;

    @Autowired
    private ParticipantIdentityService identities;

    private Organization organization;
    private OrganizationParticipant administrator;

    @BeforeEach
    public void createOrganization() throws Exception {
        organization = await(organizations.create(new Organization().setName("Application sign-up " + suffix())
                                                                    .setDescription("Organization of one application sign-up test")));
        administrator = new DefaultOrganizationParticipant("signup-admin-" + suffix(), organization.getId(),
                                                           Map.of(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                                                                  "displayName", "Sign-up Admin"),
                                                           List.of("ADMIN"));
    }

    private <T> Future<T> asAdministrator(Supplier<Future<T>> call) {
        return runAs(administrator, call);
    }

    @Test
    public void aUserSignedUpThroughKinoticsRegistrationSignsInThroughTheApplicationsOwn() throws Exception {
        String appId = "signup-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Switching application", TenantManagement.SYSTEM)));
        ApplicationKey application = new ApplicationKey(organization.getId(), appId);
        ApplicationOidcConfiguration microsoft = new ApplicationOidcConfiguration().setApplicationId(appId)
                                                                                   .setClientRegistrationType(ClientRegistrationType.MANAGED);
        microsoft.setOrganizationId(organization.getId())
                 .setCreateUserOnSignIn(true);
        microsoft.setName("Microsoft")
                 .setProvider(OidcProviderType.AZURE_AD)
                 .setEnabled(true);
        ApplicationOidcConfiguration managed = await(asAdministrator(() -> oidcConfigurations.save(microsoft, null)));

        String objectId = UUID.randomUUID().toString();
        String email = "switch-" + suffix() + "@example.test";
        // Microsoft gives each client registration a sub of its own for the same person
        ApplicationParticipantIdentity signedUp = await(signUps.findOrCreateOidcUser(application, null, managed,
                                                                                     entraClaims("sub-under-kinotic", WORK_DIRECTORY, objectId, email, true)));
        assertNotNull(signedUp);
        assertEquals(email, signedUp.getEmail());
        assertNotNull(signedUp.getTenantId(), "a user the system's tenancy admits gets a tenant of its own");

        managed.setClientRegistrationType(ClientRegistrationType.OWN)
               .setProviderAccount("common")
               .setClientId("switching-entra-client");
        ApplicationOidcConfiguration own = await(asAdministrator(() -> oidcConfigurations.save(managed, "switching-secret")));
        ApplicationParticipantIdentity signedIn = await(signUps.findOrCreateOidcUser(application, null, own,
                                                                                     entraClaims("sub-under-own", WORK_DIRECTORY, objectId, email, true)));
        assertEquals(signedUp.getId(), signedIn.getId());

        // switched back to its own registration with no client of its own, it would list a provider that always fails
        own.setClientRegistrationType(ClientRegistrationType.OWN).setClientId(null);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.save(own, null)));
    }

    @Test
    public void aMicrosoftEmailSignsUpOnlyWhenTheTokenProvesIt() throws Exception {
        String appId = "signup-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Entra application", TenantManagement.SYSTEM)));
        ApplicationKey application = new ApplicationKey(organization.getId(), appId);
        ApplicationOidcConfiguration microsoft = new ApplicationOidcConfiguration().setApplicationId(appId)
                                                                                   .setClientRegistrationType(ClientRegistrationType.MANAGED);
        microsoft.setOrganizationId(organization.getId())
                 .setCreateUserOnSignIn(true);
        // a client id left from the organization's own registration, with no directory, does not stop Kinotic's being used
        microsoft.setName("Microsoft")
                 .setProvider(OidcProviderType.AZURE_AD)
                 .setClientId("leftover-entra-client")
                 .setEnabled(true);
        ApplicationOidcConfiguration managed = await(asAdministrator(() -> oidcConfigurations.save(microsoft, null)));

        // any directory's administrator sets its users' email, so a bare Entra email would hold the address against its owner
        String claimed = "claimed-" + suffix() + "@example.test";
        assertInstanceOf(UnverifiedEmailException.class,
                         failure(() -> signUps.findOrCreateOidcUser(application, null, managed,
                                                                    entraClaims("sub-" + suffix(), WORK_DIRECTORY, UUID.randomUUID().toString(), claimed, false))));
        assertNull(await(identities.findApplicationUserByEmail(claimed, organization.getId(), appId)));

        // a personal account's email is Microsoft's to verify, and xms_edov proves a work directory's
        String personal = "personal-" + suffix() + "@example.test";
        assertEquals(personal, await(signUps.findOrCreateOidcUser(application, null, managed,
                                                                  entraClaims("sub-" + suffix(), PERSONAL_ACCOUNTS, UUID.randomUUID().toString(), personal, false))).getEmail());
        String domainOwned = "owned-" + suffix() + "@example.test";
        assertEquals(domainOwned, await(signUps.findOrCreateOidcUser(application, null, managed,
                                                                     entraClaims("sub-" + suffix(), WORK_DIRECTORY, UUID.randomUUID().toString(), domainOwned, true))).getEmail());
    }

    @Test
    public void newPeopleSignUpOnlyWhereTheyGetATenant() throws Exception {
        String appId = "signup-app-" + suffix();
        Application application = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Tenanted application", null)));
        ApplicationKey key = new ApplicationKey(organization.getId(), appId);
        Tenant globex = await(asAdministrator(() -> tenantService.createTenantIfNotExist(appId, "Globex", "A customer")));
        ApplicationOidcConfiguration appGoogle = await(asAdministrator(() -> oidcConfigurations.save(google(new ApplicationOidcConfiguration().setApplicationId(appId)), null)));
        TenantOidcConfiguration tenantGoogle = await(asAdministrator(() -> oidcConfigurations.save(google(new TenantOidcConfiguration().setApplicationId(appId)
                                                                                                                                         .setTenantId(globex.getId())), null)));
        await(oidcConfigurations.syncIndex());

        // the application's tenants are its administrators', so its own login signs no one up
        ApplicationLoginMethods atApplication = await(signUps.findLoginMethods(key, null));
        assertFalse(atApplication.passwordSignUp());
        assertEquals(List.of(new ApplicationLoginProvider(appGoogle.getId(), appGoogle.getName(), OidcProviderType.GOOGLE, false)),
                     atApplication.providers());
        assertNull(await(signUps.findOrCreateOidcUser(key, null, appGoogle, googleClaims("app-" + suffix()))));
        application.setPasswordSignUp(true);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> applicationService.saveSync(application)));

        // the tenant's own provider signs people up into the tenant, and the application's does not there either
        ApplicationLoginMethods atTenant = await(signUps.findLoginMethods(key, globex.getId()));
        assertFalse(atTenant.passwordSignUp());
        assertEquals(List.of(true, false), atTenant.providers().stream().map(ApplicationLoginProvider::signUp).toList());
        ApplicationParticipantIdentity tenantUser = await(signUps.findOrCreateOidcUser(key, globex.getId(), tenantGoogle, googleClaims("tenant-" + suffix())));
        assertEquals(globex.getId(), tenantUser.getTenantId());
        assertNull(await(signUps.findOrCreateOidcUser(key, globex.getId(), appGoogle, googleClaims("tenant-app-" + suffix()))));
    }

    @Test
    public void aPasswordSignUpCreatesTheUserOnceTheEmailIsVerified() throws Exception {
        String appId = "signup-app-" + suffix();
        Application application = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Password application", TenantManagement.SYSTEM)));
        ApplicationKey key = new ApplicationKey(organization.getId(), appId);
        String email = "password-" + suffix() + "@example.test";
        assertInstanceOf(IllegalStateException.class, failure(() -> signUps.initiatePasswordSignUp(key, email, "Pat", "https://ui.example.test")));

        await(asAdministrator(() -> applicationService.saveSync(application.setPasswordSignUp(true))));
        assertTrue(await(signUps.findLoginMethods(key, null)).passwordSignUp());
        await(signUps.initiatePasswordSignUp(key, email, "Pat", "https://ui.example.test"));
        List<PendingApplicationSignUp> pending = pendingFor(email);
        assertEquals(1, pending.size());
        String token = pending.getFirst().getVerificationToken();

        // a token carried to another application's host signs up no one there
        ApplicationKey other = new ApplicationKey(organization.getId(), "another-app-" + suffix());
        assertInstanceOf(IllegalArgumentException.class, failure(() -> signUps.completePasswordSignUp(other, token, "a-good-password")));
        // an absent password is refused as an invalid request
        assertInstanceOf(IllegalArgumentException.class, assertThrows(RuntimeException.class, () -> signUps.completePasswordSignUp(key, token, null)));

        ApplicationParticipantIdentity user = await(signUps.completePasswordSignUp(key, token, "a-good-password"));
        assertEquals(email, user.getEmail());
        assertNotNull(user.getTenantId());
        assertEquals(user.getId(), await(localAuthentication.authenticateApplicationUser(email, "a-good-password", organization.getId(), appId)).getId());
        assertInstanceOf(IllegalArgumentException.class, failure(() -> signUps.completePasswordSignUp(key, token, "a-good-password")));

        // an email that has a user is sent nothing, and the call answers as it does for any other
        await(signUps.initiatePasswordSignUp(key, email, "Pat Again", "https://ui.example.test"));
        assertTrue(pendingFor(email).isEmpty());
    }

    private List<PendingApplicationSignUp> pendingFor(String email) throws Exception {
        await(pendingSignUps.syncIndex());
        return await(pendingSignUps.findAll(Pageable.ofSize(1000))).getContent().stream()
                                                                    .filter(pending -> email.equals(pending.getEmail()))
                                                                    .toList();
    }

    private <C extends OwnedOidcConfiguration> C google(C configuration) {
        configuration.setOrganizationId(organization.getId())
                     .setCreateUserOnSignIn(true);
        configuration.setName("Google")
                     .setProvider(OidcProviderType.GOOGLE)
                     .setClientId("signup-google-client")
                     .setEnabled(true);
        return configuration;
    }

    private static Map<String, Object> entraClaims(String sub, String directory, String objectId, String email, boolean domainOwnerVerified) {
        return Map.of("sub", sub, "tid", directory, "oid", objectId, "email", email, "name", "Pat Switch", "xms_edov", domainOwnerVerified);
    }

    private Map<String, Object> googleClaims(String sub) {
        return Map.of("sub", sub, "email", sub + "@example.test", "email_verified", true, "name", "Pat Google");
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(asAdministrator(call::get))).getCause();
    }
}
