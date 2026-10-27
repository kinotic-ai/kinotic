package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.exceptions.UnverifiedEmailException;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.LoginProviderType;
import org.kinotic.domain.api.model.security.ApplicationLoginProvider;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.ClientRegistrationType;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.PendingApplicationSignUp;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.DefaultOrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ApplicationSignUpService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.internal.api.repositories.PendingApplicationSignUpRepository;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.TenantService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how people become users of an application: a user a provider signed up through Kinotic's registration
 * is the same user once the configuration switches to the application's own registration; a Microsoft email signs up
 * through a registration admitting every directory only when the token proves it; an application admits new
 * people at its own login only while the system manages its tenants, which gives each a tenant, and a tenant's
 * login admits them into the tenant through the tenant's own providers only; and a sign-up with an email and a
 * password creates the user once the emailed token comes back with a password, at the application it was for.
 */
@SpringBootTest
public class ApplicationSignUpServiceTests extends KinoticTestBase {

    // a work directory, whose administrator sets its users' email claims; personal accounts live in Microsoft's own
    private static final String WORK_DIRECTORY = "4a6c1d2e-8b3f-4c5d-9e7f-0a1b2c3d4e5f";
    private static final String PERSONAL_ACCOUNTS = "9188040d-6c67-4c5b-b112-36a304b66dad";
    private static final String UI_ORIGIN = "https://ui.example.test";

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
    private PasswordConfigurationService passwords;

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
        LoginScope application = new LoginScope(await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Switching application", TenantManagement.SYSTEM))), null);
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
        ApplicationParticipantIdentity signedUp = await(signUps.findOrCreateOidcUser(application, managed,
                                                                                     entraClaims("sub-under-kinotic", WORK_DIRECTORY, objectId, email, true)));
        assertNotNull(signedUp);
        assertEquals(email, signedUp.getEmail());
        assertNotNull(signedUp.getTenantId(), "a user the system's tenancy admits gets a tenant of its own");

        managed.setClientRegistrationType(ClientRegistrationType.OWN)
               .setProviderAccount("common")
               .setClientId("switching-entra-client");
        ApplicationOidcConfiguration own = await(asAdministrator(() -> oidcConfigurations.save(managed, "switching-secret")));
        ApplicationParticipantIdentity signedIn = await(signUps.findOrCreateOidcUser(application, own,
                                                                                     entraClaims("sub-under-own", WORK_DIRECTORY, objectId, email, true)));
        assertEquals(signedUp.getId(), signedIn.getId());

        // switched back to its own registration with no client of its own, it would list a provider that always fails
        own.setClientRegistrationType(ClientRegistrationType.OWN).setClientId(null);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> oidcConfigurations.save(own, null)));
    }

    @Test
    public void aMicrosoftEmailSignsUpThroughEveryDirectoryOnlyWhenTheTokenProvesIt() throws Exception {
        String appId = "signup-app-" + suffix();
        LoginScope application = new LoginScope(await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Entra application", TenantManagement.SYSTEM))), null);
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
                         failure(() -> signUps.findOrCreateOidcUser(application, managed,
                                                                    entraClaims("sub-" + suffix(), WORK_DIRECTORY, UUID.randomUUID().toString(), claimed, false))));
        assertNull(await(identities.findApplicationUserByEmail(claimed, organization.getId(), appId)));

        // a personal account's email is Microsoft's to verify, and xms_edov proves a work directory's
        String personal = "personal-" + suffix() + "@example.test";
        assertEquals(personal, await(signUps.findOrCreateOidcUser(application, managed,
                                                                  entraClaims("sub-" + suffix(), PERSONAL_ACCOUNTS, UUID.randomUUID().toString(), personal, false))).getEmail());
        String domainOwned = "owned-" + suffix() + "@example.test";
        assertEquals(domainOwned, await(signUps.findOrCreateOidcUser(application, managed,
                                                                     entraClaims("sub-" + suffix(), WORK_DIRECTORY, UUID.randomUUID().toString(), domainOwned, true))).getEmail());

        // a registration of the organization's own naming one directory trusts that directory's administrator
        ApplicationOidcConfiguration ownDirectory = new ApplicationOidcConfiguration().setApplicationId(appId);
        ownDirectory.setOrganizationId(organization.getId())
                    .setProviderAccount(WORK_DIRECTORY)
                    .setCreateUserOnSignIn(true);
        ownDirectory.setName("Acme Entra")
                    .setProvider(OidcProviderType.AZURE_AD)
                    .setClientId("acme-entra-client")
                    .setEnabled(true);
        ApplicationOidcConfiguration acme = await(asAdministrator(() -> oidcConfigurations.save(ownDirectory, "acme-entra-secret")));
        String employee = "employee-" + suffix() + "@example.test";
        assertEquals(employee, await(signUps.findOrCreateOidcUser(application, acme,
                                                                  entraClaims("sub-" + suffix(), WORK_DIRECTORY, UUID.randomUUID().toString(), employee, false))).getEmail());
    }

    @Test
    public void newPeopleSignUpOnlyWhereTheyGetATenant() throws Exception {
        String appId = "signup-app-" + suffix();
        Application application = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Tenanted application", null)));
        Tenant globex = await(asAdministrator(() -> tenantService.createTenantIfNotExist(appId, "Globex", "A customer")));
        LoginScope atApplication = new LoginScope(application, null);
        LoginScope atGlobex = new LoginScope(application, globex.getId());
        ApplicationOidcConfiguration appGoogle = await(asAdministrator(() -> oidcConfigurations.save(google(new ApplicationOidcConfiguration().setApplicationId(appId)), null)));
        TenantOidcConfiguration tenantGoogle = await(asAdministrator(() -> oidcConfigurations.save(google(new TenantOidcConfiguration().setApplicationId(appId)
                                                                                                                                         .setTenantId(globex.getId())), null)));
        await(oidcConfigurations.syncIndex());

        // the application's tenants are its administrators', so its own login signs no one up
        assertEquals(List.of(new ApplicationLoginProvider(appGoogle.getId(), LoginProviderType.OIDC, appGoogle.getName(), OidcProviderType.GOOGLE, false)),
                     await(signUps.findLoginProviders(atApplication)));
        assertNull(await(signUps.findOrCreateOidcUser(atApplication, appGoogle, googleClaims("app-" + suffix()))));
        // a tenant's provider signs people up into its tenant only, so at the application's login it signs no one up
        assertNull(await(signUps.findOrCreateOidcUser(atApplication, tenantGoogle, googleClaims("app-tenant-" + suffix()))));
        // nor does its password, which signs its users in at its own login and its tenants', and is refused sign-up
        assertInstanceOf(IllegalArgumentException.class, failure(() -> passwords.save(password(appId, null).setCreateUserOnSignIn(true))));
        PasswordConfiguration appPassword = await(asAdministrator(() -> passwords.save(password(appId, null))));
        ApplicationLoginProvider passwordProvider = new ApplicationLoginProvider(appPassword.getId(), LoginProviderType.PASSWORD,
                                                                                 PasswordConfiguration.NAME, null, false);
        assertEquals(passwordProvider, await(signUps.findLoginProviders(atApplication)).getFirst());

        // the tenant's own provider signs people up into the tenant, and the application's provider and password do not there
        List<ApplicationLoginProvider> tenantProviders = await(signUps.findLoginProviders(atGlobex));
        assertEquals(passwordProvider, tenantProviders.getFirst());
        assertEquals(List.of(false, true, false), tenantProviders.stream().map(ApplicationLoginProvider::signUp).toList());
        String tenantSubject = "tenant-" + suffix();
        ApplicationParticipantIdentity tenantUser = await(signUps.findOrCreateOidcUser(atGlobex, tenantGoogle, googleClaims(tenantSubject)));
        assertEquals(globex.getId(), tenantUser.getTenantId());
        assertNull(await(signUps.findOrCreateOidcUser(atGlobex, appGoogle, googleClaims("tenant-app-" + suffix()))));
        // the tenant's user signs in again as the same user
        assertEquals(tenantUser.getId(), await(signUps.findOrCreateOidcUser(atGlobex, tenantGoogle, googleClaims(tenantSubject))).getId());
    }

    @Test
    public void aPasswordSignUpCreatesTheUserOnceTheEmailIsVerified() throws Exception {
        String appId = "signup-app-" + suffix();
        Application application = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Password application", TenantManagement.SYSTEM)));
        String email = "password-" + suffix() + "@example.test";
        assertInstanceOf(IllegalStateException.class, failure(() -> signUps.initiatePasswordSignUp(new LoginScope(application, null), email, UI_ORIGIN)));

        await(asAdministrator(() -> passwords.save(password(appId, null).setCreateUserOnSignIn(true))));
        LoginScope scope = new LoginScope(application, null);
        assertTrue(await(signUps.findLoginProviders(scope)).getFirst().signUp());
        // the email is matched and stored trimmed and lowercased
        await(signUps.initiatePasswordSignUp(scope, "  " + email.toUpperCase(Locale.ROOT) + " ", UI_ORIGIN));
        List<PendingApplicationSignUp> pending = pendingFor(email);
        assertEquals(1, pending.size());
        String first = pending.getFirst().getVerificationToken();

        // while the link sent is valid, a repeated request sends no other
        await(signUps.initiatePasswordSignUp(scope, email, UI_ORIGIN));
        assertEquals(List.of(first), tokens(pendingFor(email)));
        // once it has expired, a request sends a new link in its place
        await(pendingSignUps.saveSync(pending.getFirst().setExpiresAt(new Date(System.currentTimeMillis() - 60_000))));
        await(signUps.initiatePasswordSignUp(scope, email, UI_ORIGIN));
        List<String> renewed = tokens(pendingFor(email));
        assertEquals(1, renewed.size());
        assertNotEquals(first, renewed.getFirst());
        String token = renewed.getFirst();

        // a tenant's login signs no one up, and a token carried to another application's host signs up no one there
        assertInstanceOf(IllegalStateException.class, failure(() -> signUps.completePasswordSignUp(new LoginScope(scope.application(), "a-tenant"), token, "a-good-password", null)));
        String otherId = "another-app-" + suffix();
        Application other = await(asAdministrator(() -> applicationService.createApplicationIfNotExist(otherId, "Another password application", TenantManagement.SYSTEM)));
        await(asAdministrator(() -> passwords.save(password(otherId, null).setCreateUserOnSignIn(true))));
        LoginScope atOther = new LoginScope(other, null);
        assertInstanceOf(IllegalArgumentException.class, failure(() -> signUps.completePasswordSignUp(atOther, token, "a-good-password", null)));
        // an absent password, or a name past 200 characters, is refused as an invalid request
        assertInstanceOf(IllegalArgumentException.class, assertThrows(RuntimeException.class, () -> signUps.completePasswordSignUp(scope, token, null, null)));
        assertInstanceOf(IllegalArgumentException.class, assertThrows(RuntimeException.class,
                                                                      () -> signUps.completePasswordSignUp(scope, token, "a-good-password", "n".repeat(201))));

        // the name is chosen by the person who holds the email, when they complete the sign-up
        ApplicationParticipantIdentity user = await(signUps.completePasswordSignUp(scope, token, "a-good-password", "Pat Signs Up"));
        assertEquals(email, user.getEmail());
        assertEquals("Pat Signs Up", user.getDisplayName());
        assertNotNull(user.getTenantId());
        assertEquals(user.getId(), await(localAuthentication.authenticateApplicationUser(email, "a-good-password", organization.getId(), appId)).getId());
        assertInstanceOf(IllegalArgumentException.class, failure(() -> signUps.completePasswordSignUp(scope, token, "a-good-password", null)));

        // an email that has a user is sent nothing, and the call answers as it does for any other
        await(signUps.initiatePasswordSignUp(scope, email, UI_ORIGIN));
        assertTrue(pendingFor(email).isEmpty());
    }

    @Test
    public void aUsersPasswordSignsInOnceItsOwnLoginTakesOne() throws Exception {
        String appId = "password-app-" + suffix();
        await(asAdministrator(() -> applicationService.createApplicationIfNotExist(appId, "Password sign-in application", null)));
        Tenant globex = await(asAdministrator(() -> tenantService.createTenantIfNotExist(appId, "Globex", "A customer")));
        Tenant initech = await(asAdministrator(() -> tenantService.createTenantIfNotExist(appId, "Initech", "Another customer")));
        String atGlobex = applicationUser(appId, globex.getId());
        String atInitech = applicationUser(appId, initech.getId());

        assertNull(await(localAuthentication.authenticateApplicationUser(atGlobex, "a-good-password", organization.getId(), appId)));

        // a tenant's own configuration takes the password of the tenant's users alone, whichever login they name
        await(asAdministrator(() -> passwords.save(password(appId, globex.getId()))));
        assertNotNull(await(localAuthentication.authenticateApplicationUser(atGlobex, "a-good-password", organization.getId(), appId)));
        assertNull(await(localAuthentication.authenticateApplicationUser(atInitech, "a-good-password", organization.getId(), appId)));

        // the application's takes every tenant's users' passwords
        await(asAdministrator(() -> passwords.save(password(appId, globex.getId()).setEnabled(false))));
        await(asAdministrator(() -> passwords.save(password(appId, null))));
        assertNotNull(await(localAuthentication.authenticateApplicationUser(atGlobex, "a-good-password", organization.getId(), appId)));
        assertNotNull(await(localAuthentication.authenticateApplicationUser(atInitech, "a-good-password", organization.getId(), appId)));

        // sign-up gives a new user a tenant of its own, so only the application's own configuration takes it
        assertInstanceOf(IllegalArgumentException.class, failure(() -> passwords.save(password(appId, globex.getId()).setCreateUserOnSignIn(true))));
        assertInstanceOf(IllegalArgumentException.class, failure(() -> passwords.save(password(null, null).setCreateUserOnSignIn(true))));

        // a tenant, and an application, created again under the same id start with no password sign-in
        await(asAdministrator(() -> tenantService.deleteById(appId, globex.getId())));
        assertNull(await(passwords.findById(PasswordConfiguration.idOf(organization.getId(), appId, globex.getId()), organization.getId())));
        await(asAdministrator(() -> applicationService.deleteById(appId)));
        assertNull(await(passwords.findById(PasswordConfiguration.idOf(organization.getId(), appId, null), organization.getId())));
    }

    // a user of the application's tenant tenantId, with the password a-good-password; returns its email
    private String applicationUser(String applicationId, String tenantId) throws Exception {
        String email = "tenant-user-" + suffix() + "@example.test";
        ApplicationParticipantIdentity user = new ApplicationParticipantIdentity();
        user.setOrganizationId(organization.getId())
            .setApplicationId(applicationId)
            .setTenantId(tenantId);
        user.setEmail(email).setDisplayName("Pat Tenant");
        await(identities.createUser(user, "a-good-password"));
        return email;
    }

    private PasswordConfiguration password(String applicationId, String tenantId) {
        return new PasswordConfiguration().setOrganizationId(organization.getId())
                                          .setApplicationId(applicationId)
                                          .setTenantId(tenantId)
                                          .setEnabled(true);
    }

    private static List<String> tokens(List<PendingApplicationSignUp> pending) {
        return pending.stream().map(PendingApplicationSignUp::getVerificationToken).toList();
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
