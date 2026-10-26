package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.exceptions.UnverifiedEmailException;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.ApplicationLoginProvider;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.LoginProviderType;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.PendingApplicationSignUp;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.rest.support.OAuth2Util;
import org.kinotic.domain.api.services.security.ApplicationSignUpService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.internal.api.repositories.PendingApplicationSignUpRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultApplicationSignUpService implements ApplicationSignUpService {

    private static final long PASSWORD_SIGN_UP_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_DISPLAY_NAME_LENGTH = 200;

    private final OidcConfigurationService oidcConfigurationService;
    private final PasswordConfigurationService passwordConfigurationService;
    private final ParticipantIdentityService identityService;
    private final PendingApplicationSignUpRepository pendingRepository;
    private final EmailService emailService;

    @Override
    public Future<List<ApplicationLoginProvider>> findLoginProviders(LoginScope scope) {
        Validate.notNull(scope, "scope cannot be null");
        ApplicationKey application = scope.applicationKey();
        Future<PasswordConfiguration> password = passwordConfigurationService.findOffered(application.organizationId(), application.applicationId(), scope.tenantId());
        Future<List<OidcConfiguration>> providers = oidcConfigurationService.findEnabledForScope(application.organizationId(), application.applicationId(), scope.tenantId());
        return Future.all(password, providers).map(v -> {
            List<ApplicationLoginProvider> ret = new ArrayList<>();
            if (password.result() != null) {
                ret.add(new ApplicationLoginProvider(password.result().getId(), LoginProviderType.PASSWORD, PasswordConfiguration.NAME,
                                                     null, signsUp(scope, password.result())));
            }
            providers.result().forEach(configuration -> ret.add(new ApplicationLoginProvider(configuration.getId(), LoginProviderType.OIDC,
                                                                                             configuration.getName(), configuration.getProvider(),
                                                                                             signsUp(scope, configuration))));
            return ret;
        });
    }

    @Override
    public Future<ApplicationParticipantIdentity> findOrCreateOidcUser(LoginScope scope,
                                                                       OwnedOidcConfiguration configuration,
                                                                       Map<String, Object> verifiedClaims) {
        Validate.notNull(scope, "scope cannot be null");
        Validate.notNull(configuration, "configuration cannot be null");
        ApplicationKey application = scope.applicationKey();
        // bound by the identifier the provider gives the person under every client, so a configuration switched between
        // Kinotic's registration and the organization's own still finds the users it signed up
        String subject = OAuth2Util.stableSubject(verifiedClaims, configuration.getProvider());
        Validate.notBlank(subject, "The verified claims carry no subject");
        return identityService.findApplicationUserByOidcIdentity(configuration.getId(), subject, application.organizationId(), application.applicationId())
                              .compose(found -> {
                                  Future<ApplicationParticipantIdentity> ret;
                                  if (found != null) {
                                      ret = Future.succeededFuture(scope.memberOf(found));
                                  } else if (signsUp(scope, configuration)) {
                                      ret = createOidcUser(scope, configuration, subject, verifiedClaims);
                                  } else {
                                      ret = Future.succeededFuture();
                                  }
                                  return ret;
                              });
    }

    private Future<ApplicationParticipantIdentity> createOidcUser(LoginScope scope,
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
        ApplicationParticipantIdentity user = newUser(scope.applicationKey(), email, OAuth2Util.firstPresent(verifiedClaims, "name", "preferred_username", "email"));
        // the system gives a user of the application a tenant of its own; a tenant's login creates the tenant's user
        user.setTenantId(scope.tenantId());
        return identityService.createOidcUser(user, configuration.getId(), subject);
    }

    @Override
    public Future<Void> initiatePasswordSignUp(LoginScope scope, String email, String uiOrigin) {
        Validate.notNull(scope, "scope cannot be null");
        Validate.isTrue(email != null && email.contains("@"), "A valid email is required");
        Validate.notBlank(uiOrigin, "The UI the verification link opens is required");
        String address = DomainUtil.normalizeEmail(email);
        ApplicationKey application = scope.applicationKey();
        return requirePasswordSignUp(scope)
                .compose(v -> identityService.findApplicationUserByEmail(address, application.organizationId(), application.applicationId()))
                .compose(existing -> existing != null
                        // the caller learns nothing either way; the person who holds the email signs in
                        ? Future.succeededFuture()
                        : sendVerification(scope.application(), address, uiOrigin));
    }

    // one sign-up per email is pending at a time, so repeated requests for an address send it one link a day
    private Future<Void> sendVerification(Application application, String email, String uiOrigin) {
        return pendingRepository.findByEmail(email, application.getOrganizationId(), application.getId())
                                .compose(earlier -> {
                                    Future<Void> ret;
                                    if (earlier != null && earlier.getExpiresAt().after(new Date())) {
                                        ret = Future.succeededFuture();
                                    } else {
                                        Future<Void> cleared = earlier != null ? pendingRepository.deleteById(earlier.getId()) : Future.succeededFuture();
                                        ret = cleared.compose(v -> savePendingAndSend(application, email, uiOrigin));
                                    }
                                    return ret;
                                });
    }

    private Future<Void> savePendingAndSend(Application application, String email, String uiOrigin) {
        Date now = new Date();
        PendingApplicationSignUp pending = new PendingApplicationSignUp()
                .setId(UUID.randomUUID().toString())
                .setVerificationToken(DomainUtil.generateUrlSafeToken(32))
                .setCreated(now)
                .setExpiresAt(new Date(now.getTime() + PASSWORD_SIGN_UP_TTL_MS))
                .setEmail(email)
                .setOrganizationId(application.getOrganizationId())
                .setApplicationId(application.getId());
        // refreshed before the email goes, so a request repeated right away finds this one
        return pendingRepository.saveSync(pending)
                                .compose(saved -> emailService.sendApplicationSignUpEmail(email, application.getName(),
                                                                                          pending.getVerificationToken(), uiOrigin)
                                                              // sendVerification sends nothing while a pending sign-up is
                                                              // valid, so one whose email never went would refuse the
                                                              // address a link for the whole TTL
                                                              .recover(err -> pendingRepository.deleteById(pending.getId())
                                                                                               .transform(v -> Future.failedFuture(err))));
    }

    @Override
    public Future<ApplicationParticipantIdentity> completePasswordSignUp(LoginScope scope, String token, String password, String displayName) {
        Validate.notNull(scope, "scope cannot be null");
        // isTrue rather than notBlank, which throws NullPointerException for an absent value
        Validate.isTrue(StringUtils.isNotBlank(token), "The verification token is required");
        Validate.isTrue(StringUtils.isNotBlank(password), "A password is required");
        Validate.isTrue(displayName == null || displayName.length() <= MAX_DISPLAY_NAME_LENGTH,
                        "A display name is at most %d characters", MAX_DISPLAY_NAME_LENGTH);
        ApplicationKey application = scope.applicationKey();
        return requirePasswordSignUp(scope)
                .compose(v -> pendingRepository.findValidByToken(token))
                .compose(pending -> {
                    // a token carried to another application's host signs up no one there
                    Validate.isTrue(application.equals(new ApplicationKey(pending.getOrganizationId(), pending.getApplicationId())),
                                    "Invalid or already consumed token.");
                    return identityService.createUser(newUser(application, pending.getEmail(), StringUtils.isNotBlank(displayName) ? displayName : null), password)
                                          .compose(created -> pendingRepository.deleteById(pending.getId()).map(created));
                });
    }

    @Override
    public Future<Boolean> offersPasswordSignUp(LoginScope scope) {
        Validate.notNull(scope, "scope cannot be null");
        ApplicationKey application = scope.applicationKey();
        return passwordConfigurationService.findOffered(application.organizationId(), application.applicationId(), scope.tenantId())
                                           .map(offered -> signsUp(scope, offered));
    }

    private Future<Void> requirePasswordSignUp(LoginScope scope) {
        return offersPasswordSignUp(scope).compose(offered -> offered
                ? Future.<Void>succeededFuture()
                : Future.failedFuture(new IllegalStateException("The login does not offer sign-up with an email and a password")));
    }

    // whether a new person signs up with an email and a password at the login of scope, which offers password
    private static boolean signsUp(LoginScope scope, PasswordConfiguration offered) {
        return offered != null && offered.isCreateUserOnSignIn() && admitsNewUsers(scope);
    }

    // whether a new person signs up through configuration at the login of scope: a tenant's own configuration admits
    // people into the tenant, the application's into tenants of their own
    private static boolean signsUp(LoginScope scope, OidcConfiguration configuration) {
        boolean ret;
        if (configuration instanceof TenantOidcConfiguration forTenant) {
            // only at its tenant's login: at the application's, the user it created would belong to no tenant
            ret = forTenant.isCreateUserOnSignIn() && forTenant.getTenantId().equals(scope.tenantId());
        } else if (configuration instanceof ApplicationOidcConfiguration forApplication) {
            ret = forApplication.isCreateUserOnSignIn() && admitsNewUsers(scope);
        } else {
            ret = false;
        }
        return ret;
    }

    // a new user of the application gets a tenant of its own only while the system manages its tenants, and a
    // tenant's administrators decide who joins a tenant, so no method of the application's admits a person into one
    private static boolean admitsNewUsers(LoginScope scope) {
        return scope.tenantId() == null && scope.application().getTenantManagement() == TenantManagement.SYSTEM;
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
