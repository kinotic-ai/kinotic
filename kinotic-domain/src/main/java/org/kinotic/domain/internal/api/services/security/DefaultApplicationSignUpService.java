package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.exceptions.UnverifiedEmailException;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.ApplicationLoginMethods;
import org.kinotic.domain.api.model.security.ApplicationLoginProvider;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.PendingApplicationSignUp;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.rest.support.OAuth2Util;
import org.kinotic.domain.api.services.security.ApplicationSignUpService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.internal.api.repositories.PendingApplicationSignUpRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultApplicationSignUpService implements ApplicationSignUpService {

    private static final long PASSWORD_SIGN_UP_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final String VERIFICATION_PATH = "/signup/verify?token=";
    private static final int MAX_DISPLAY_NAME_LENGTH = 200;

    private final ApplicationRepository applicationRepository;
    private final OidcConfigurationService oidcConfigurationService;
    private final ParticipantIdentityService identityService;
    private final PendingApplicationSignUpRepository pendingRepository;
    private final EmailService emailService;

    @Override
    public Future<ApplicationLoginMethods> findLoginMethods(ApplicationKey application, String tenantId) {
        Validate.notNull(application, "application cannot be null");
        Future<Application> found = requireApplication(application);
        Future<List<OidcConfiguration>> offered = oidcConfigurationService.findEnabledForScope(application.organizationId(),
                                                                                              application.applicationId(), tenantId);
        return Future.all(found, offered).map(v -> new ApplicationLoginMethods(
                offersPasswordSignUp(found.result(), tenantId),
                offered.result().stream()
                       .map(configuration -> new ApplicationLoginProvider(configuration.getId(),
                                                                          configuration.getName(),
                                                                          configuration.getProvider(),
                                                                          signsUp(found.result(), tenantId, configuration)))
                       .toList()));
    }

    @Override
    public Future<Boolean> offersPasswordSignUp(ApplicationKey application, String tenantId) {
        Validate.notNull(application, "application cannot be null");
        return requireApplication(application).map(found -> offersPasswordSignUp(found, tenantId));
    }

    @Override
    public Future<ApplicationParticipantIdentity> findOrCreateOidcUser(ApplicationKey application,
                                                                       String tenantId,
                                                                       OwnedOidcConfiguration configuration,
                                                                       Map<String, Object> verifiedClaims) {
        Validate.notNull(application, "application cannot be null");
        Validate.notNull(configuration, "configuration cannot be null");
        // bound by the identifier the provider gives the person under every client, so a configuration switched between
        // Kinotic's registration and the organization's own still finds the users it signed up
        String subject = OAuth2Util.stableSubject(verifiedClaims, configuration.getProvider());
        Validate.notBlank(subject, "The verified claims carry no subject");
        return identityService.findApplicationUserByOidcIdentity(configuration.getId(), subject, application.organizationId(), application.applicationId())
                              .compose(found -> {
                                  Future<ApplicationParticipantIdentity> ret;
                                  if (found != null) {
                                      // a tenant's login admits the tenant's users only
                                      ret = Future.succeededFuture(tenantId == null || tenantId.equals(found.getTenantId()) ? found : null);
                                  } else if (!configuration.isCreateUserOnSignIn()) {
                                      ret = Future.succeededFuture();
                                  } else {
                                      ret = requireApplication(application).compose(app -> signsUp(app, tenantId, configuration)
                                              ? createOidcUser(application, tenantId, configuration, subject, verifiedClaims)
                                              : Future.succeededFuture());
                                  }
                                  return ret;
                              });
    }

    private Future<ApplicationParticipantIdentity> createOidcUser(ApplicationKey application,
                                                                  String tenantId,
                                                                  OwnedOidcConfiguration configuration,
                                                                  String subject,
                                                                  Map<String, Object> verifiedClaims) {
        // where every directory at the provider is admitted, the email the provider vouches for may be one a stranger's
        // directory set, and a user created under it would hold the email against its owner; a configuration naming one
        // directory trusts that directory's administrator, as its owner chose to
        if (admitsEveryDirectory(configuration) && !OAuth2Util.isEmailProven(verifiedClaims, configuration.getProvider())) {
            return Future.failedFuture(new UnverifiedEmailException("The provider does not prove the email belongs to the person signing in"));
        }
        String email = OAuth2Util.stringClaim(verifiedClaims, "email");
        ApplicationParticipantIdentity user = newUser(application, email, OAuth2Util.firstPresent(verifiedClaims, "name", "preferred_username", "email"));
        // the system gives a user of the application a tenant of its own; a tenant's login creates the tenant's user
        user.setTenantId(tenantId);
        return identityService.createOidcUser(user, configuration.getId(), subject);
    }

    @Override
    public Future<Void> initiatePasswordSignUp(ApplicationKey application, String email, String displayName, String uiOrigin) {
        Validate.notNull(application, "application cannot be null");
        Validate.isTrue(email != null && email.contains("@"), "A valid email is required");
        Validate.notBlank(uiOrigin, "The UI the verification link opens is required");
        Validate.isTrue(displayName == null || displayName.length() <= MAX_DISPLAY_NAME_LENGTH,
                        "A display name is at most %d characters", MAX_DISPLAY_NAME_LENGTH);
        return requirePasswordSignUp(application)
                .compose(found -> identityService.findApplicationUserByEmail(email, application.organizationId(), application.applicationId())
                                                 .compose(existing -> existing != null
                                                         // the caller learns nothing either way; the person who holds the email signs in
                                                         ? Future.succeededFuture()
                                                         : sendVerification(found, email, displayName, uiOrigin)));
    }

    private Future<Void> sendVerification(Application application, String email, String displayName, String uiOrigin) {
        Date now = new Date();
        PendingApplicationSignUp pending = new PendingApplicationSignUp()
                .setId(UUID.randomUUID().toString())
                .setVerificationToken(DomainUtil.generateUrlSafeToken(32))
                .setCreated(now)
                .setExpiresAt(new Date(now.getTime() + PASSWORD_SIGN_UP_TTL_MS))
                .setEmail(email)
                .setDisplayName(displayName != null && !displayName.isBlank() ? displayName : null)
                .setOrganizationId(application.getOrganizationId())
                .setApplicationId(application.getId());
        return pendingRepository.save(pending)
                                // the name is whatever the requester typed, so it stays out of an email sent to an
                                // address nobody has proven is theirs
                                .compose(saved -> emailService.sendApplicationSignUpEmail(email,
                                                                                          application.getName(),
                                                                                          uiOrigin + VERIFICATION_PATH + pending.getVerificationToken()));
    }

    @Override
    public Future<ApplicationParticipantIdentity> completePasswordSignUp(ApplicationKey application, String token, String password) {
        Validate.notNull(application, "application cannot be null");
        // isTrue rather than notBlank, which throws NullPointerException for an absent value
        Validate.isTrue(StringUtils.isNotBlank(token), "The verification token is required");
        Validate.isTrue(StringUtils.isNotBlank(password), "A password is required");
        return pendingRepository.findValidByToken(token)
                .compose(pending -> {
                    // a token carried to another application's host signs up no one there
                    Validate.isTrue(application.equals(new ApplicationKey(pending.getOrganizationId(), pending.getApplicationId())),
                                    "Invalid or already consumed token.");
                    return requirePasswordSignUp(application)
                            .compose(found -> identityService.createUser(newUser(application, pending.getEmail(), pending.getDisplayName()), password))
                            .compose(created -> pendingRepository.deleteById(pending.getId()).map(created));
                });
    }

    private Future<Application> requireApplication(ApplicationKey application) {
        return applicationRepository.findById(application.applicationId(), application.organizationId()).map(found -> {
            Validate.isTrue(found != null, "Organization %s has no application %s", application.organizationId(), application.applicationId());
            return found;
        });
    }

    private Future<Application> requirePasswordSignUp(ApplicationKey application) {
        return requireApplication(application).map(found -> {
            if (!offersPasswordSignUp(found, null)) {
                throw new IllegalStateException("The application does not offer sign-up with an email and a password");
            }
            return found;
        });
    }

    private static boolean offersPasswordSignUp(Application application, String tenantId) {
        return application.isPasswordSignUp() && admitsNewUsers(application, tenantId);
    }

    // whether a new person signs up through configuration at the login of application, or of its tenant tenantId: a
    // tenant's own configuration admits people into the tenant, the application's into tenants of their own
    private static boolean signsUp(Application application, String tenantId, OidcConfiguration configuration) {
        boolean ret;
        if (configuration instanceof TenantOidcConfiguration forTenant) {
            ret = forTenant.isCreateUserOnSignIn();
        } else if (configuration instanceof ApplicationOidcConfiguration forApplication) {
            ret = forApplication.isCreateUserOnSignIn() && admitsNewUsers(application, tenantId);
        } else {
            ret = false;
        }
        return ret;
    }

    // a new user of the application gets a tenant of its own only while the system manages its tenants, and a
    // tenant's administrators decide who joins a tenant, so no method of the application's admits a person into one
    private static boolean admitsNewUsers(Application application, String tenantId) {
        return tenantId == null && application.getTenantManagement() == TenantManagement.SYSTEM;
    }

    // Kinotic's registration admits every directory, as an organization's own one naming no single directory does
    private static boolean admitsEveryDirectory(OwnedOidcConfiguration configuration) {
        return OidcConfigurationService.isManaged(configuration)
                || configuration.getProvider().admitsEveryDirectory(configuration.getProviderAccount());
    }

    private static ApplicationParticipantIdentity newUser(ApplicationKey application, String email, String displayName) {
        ApplicationParticipantIdentity ret = new ApplicationParticipantIdentity();
        ret.setOrganizationId(application.organizationId())
           .setApplicationId(application.applicationId());
        ret.setEmail(email)
           .setDisplayName(displayName);
        return ret;
    }
}
