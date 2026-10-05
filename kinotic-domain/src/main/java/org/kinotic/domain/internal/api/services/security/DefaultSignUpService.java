package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.idl.api.utils.AuthzUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.repositories.TenantRepository;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.OnboardingMechanism;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.PendingSignUp;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.SignUpService;
import org.kinotic.domain.internal.api.repositories.PendingSignUpRepository;
import org.kinotic.domain.internal.api.services.EmailService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSignUpService implements SignUpService {

    private static final long LOCAL_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final long OIDC_TTL_MS = 10 * 60 * 1000L;       // 10 minutes

    private final PendingSignUpRepository pendingSignUpRepository;
    private final ParticipantIdentityService identityService;
    private final OrganizationService organizationService;
    private final RelationshipService relationships;
    private final EmailService emailService;
    private final ApplicationRepository applications;
    private final TenantRepository tenants;

    @Override
    public Future<Void> initiateLocalSignUp(String email, String displayName) {
        Validate.notBlank(email, "Email is required");
        Validate.notBlank(displayName, "Display name is required");

        return pendingSignUpRepository.findByEmail(email)
                .compose(existing -> {
                    if (existing != null) {
                        return Future.failedFuture(new IllegalArgumentException(
                                "A sign-up is already pending for this email. Check your inbox for the verification link."));
                    }
                    return identityService.findFirstOrgUserByEmail(email);
                })
                .compose(existingUser -> {
                    if (existingUser != null) {
                        return Future.failedFuture(new IllegalArgumentException(
                                "An account with this email already exists."));
                    }
                    String token = UUID.randomUUID().toString();
                    Date now = new Date();
                    PendingSignUp pending = new PendingSignUp()
                            .setId(UUID.randomUUID().toString())
                            .setEmail(email)
                            .setDisplayName(displayName)
                            .setAuthType(AuthType.LOCAL)
                            .setVerificationToken(token)
                            .setCreated(now)
                            .setExpiresAt(new Date(now.getTime() + LOCAL_TTL_MS));
                    return pendingSignUpRepository.save(pending)
                            .compose(saved -> emailService.sendVerificationEmail(email, displayName, token));
                });
    }

    @Override
    public Future<PendingSignUp> createOidcPending(PendingSignUp pending) {
        Validate.notBlank(pending.getOidcSubject(), "oidcSubject is required");
        Validate.notBlank(pending.getOidcConfigId(), "oidcConfigId is required");
        Validate.notBlank(pending.getEmail(), "email is required");

        Date now = new Date();
        pending.setId(UUID.randomUUID().toString())
               .setAuthType(AuthType.OIDC)
               .setVerificationToken(UUID.randomUUID().toString())
               .setCreated(now)
               .setExpiresAt(new Date(now.getTime() + OIDC_TTL_MS));
        return pendingSignUpRepository.saveSync(pending);
    }

    @Override
    public Future<UserParticipantIdentity> completeLocalSignUp(String token, String orgName, String orgDescription, String password) {
        Validate.notBlank(token, "Verification token is required");
        Validate.notBlank(orgName, "Organization name is required");
        Validate.notBlank(password, "Password is required");

        return pendingSignUpRepository.findValidByToken(token)
                .compose(pending -> createOrgWithAdmin(orgName, orgDescription, newUser(pending), password)
                        .compose(savedAdmin -> pendingSignUpRepository.deleteById(pending.getId())
                                .map(savedAdmin)));
    }

    @Override
    public Future<UserParticipantIdentity> completeOidcWithNewOrg(String token, String orgName, String orgDescription) {
        Validate.notBlank(orgName, "Organization name is required");
        return pendingSignUpRepository.findValidByToken(token)
                .compose(pending -> createOrgWithAdmin(orgName, orgDescription, newUser(pending), null)
                        .compose(savedAdmin -> pendingSignUpRepository.deleteById(pending.getId())
                                .map(savedAdmin)));
    }

    @Override
    public Future<Void> initiateTenantSignUp(ApplicationKey applicationKey, String email, String displayName) {
        Validate.notNull(applicationKey, "applicationKey cannot be null");
        Validate.notBlank(email, "Email is required");
        Validate.notBlank(displayName, "Display name is required");
        String normalized = DomainUtil.normalizeEmail(email);
        return requireSignUpOffered(applicationKey)
                .compose(application -> pendingSignUpRepository.findByEmailAndApplication(normalized, applicationKey.organizationId(), applicationKey.applicationId())
                        .compose(existing -> {
                            if (existing != null) {
                                return Future.failedFuture(new IllegalArgumentException(
                                        "A sign-up is already pending for this email. Check your inbox for the verification link."));
                            }
                            return identityService.findByEmail(normalized, applicationKey.organizationId(), applicationKey.applicationId());
                        })
                        .compose(existingUser -> {
                            if (existingUser != null) {
                                return Future.failedFuture(new IllegalArgumentException("An account with this email already exists."));
                            }
                            String token = UUID.randomUUID().toString();
                            Date now = new Date();
                            PendingSignUp pending = new PendingSignUp()
                                    .setId(UUID.randomUUID().toString())
                                    .setEmail(normalized)
                                    .setDisplayName(displayName)
                                    .setAuthType(AuthType.LOCAL)
                                    .setOrganizationId(applicationKey.organizationId())
                                    .setApplicationId(applicationKey.applicationId())
                                    .setVerificationToken(token)
                                    .setCreated(now)
                                    .setExpiresAt(new Date(now.getTime() + LOCAL_TTL_MS));
                            return pendingSignUpRepository.save(pending)
                                    .compose(saved -> emailService.sendVerificationEmail(normalized, displayName, token, application.getPrimaryUiUrl()));
                        }));
    }

    @Override
    public Future<UserParticipantIdentity> completeTenantSignUp(String token, String tenantName, String password) {
        Validate.notBlank(token, "Verification token is required");
        Validate.notBlank(tenantName, "Tenant name is required");
        Validate.notBlank(password, "Password is required");
        return pendingSignUpRepository.findValidByToken(token)
                .compose(pending -> {
                    if (pending.getApplicationId() == null) {
                        return Future.failedFuture(new IllegalArgumentException("This link belongs to an organization sign-up."));
                    }
                    ApplicationKey applicationKey = new ApplicationKey(pending.getOrganizationId(), pending.getApplicationId());
                    return requireSignUpOffered(applicationKey)
                            .compose(application -> createTenantWithAdmin(applicationKey, tenantName, newUser(pending), password))
                            .compose(savedAdmin -> pendingSignUpRepository.deleteById(pending.getId()).map(savedAdmin));
                });
    }

    // The application, which must exist and offer sign-up, with a primary UI the verification link leads into
    private Future<Application> requireSignUpOffered(ApplicationKey applicationKey) {
        return applications.findById(applicationKey.applicationId(), applicationKey.organizationId()).map(application -> {
            if (application == null) {
                throw new IllegalArgumentException("Application not found.");
            }
            if (!application.getOnboarding().contains(OnboardingMechanism.TENANT_SIGN_UP)) {
                throw new IllegalArgumentException("This application does not offer sign-up.");
            }
            Validate.validState(application.getPrimaryUiUrl() != null,
                                "The application '%s' has no primary UI for its sign-up link to lead into", application.getId());
            return application;
        });
    }

    /**
     * Creates the tenant (failing if the application has one of the name), makes {@code admin} its first user
     * and creator, and binds the admin as the tenant's administrator in the application's store, so the admin
     * holds every permission on the tenant's rows and on the tenant itself.
     */
    private Future<UserParticipantIdentity> createTenantWithAdmin(ApplicationKey applicationKey,
                                                                  String tenantName,
                                                                  UserParticipantIdentity admin,
                                                                  String password) {
        String tenantId = DomainUtil.slugifyId(tenantName);
        Tenant tenant = DomainUtil.createTenant(applicationKey, tenantId, tenantName);
        return tenants.findByTenantId(applicationKey.organizationId(), applicationKey.applicationId(), tenantId)
                      .compose(existing -> {
                          if (existing != null) {
                              return Future.failedFuture(new IllegalArgumentException("A tenant named '" + tenantName + "' already exists."));
                          }
                          return tenants.createSync(tenant, applicationKey.organizationId());
                      })
                      .compose(savedTenant -> {
                          admin.setOrganizationId(applicationKey.organizationId())
                               .setApplicationId(applicationKey.applicationId())
                               .setTenantId(savedTenant.getTenantId());
                          return identityService.createUser(admin, password)
                                  .compose(savedAdmin -> {
                                      savedTenant.setCreatedBy(savedAdmin.getId());
                                      // the customer administers the tenant: a binding of the tenant admin role on the tenant,
                                      // which reaches every row of every definition in it
                                      return tenants.save(savedTenant, applicationKey.organizationId())
                                              .compose(v -> relationships.bind(applicationKey.applicationId(),
                                                                               AuthzUtil.roleId(AuthzUtil.TENANT_TYPE, AuthzUtil.ADMIN),
                                                                               AuthzUtil.object(AuthzUtil.USER_TYPE, savedAdmin.getId()),
                                                                               AuthzUtil.object(AuthzUtil.TENANT_TYPE, savedTenant.getTenantId())))
                                              .map(savedAdmin);
                                  });
                      });
    }

    /**
     * Creates the organization (failing if the name is taken), makes {@code admin} its first
     * member and creator. The admin (and its credential, when
     * {@code password} is non-null) is created through {@link ParticipantIdentityService#createUser}
     * so member creation has a single code path.
     */
    private Future<UserParticipantIdentity> createOrgWithAdmin(String orgName, String orgDescription, UserParticipantIdentity admin, String password) {
        Organization org = new Organization().setName(orgName).setDescription(orgDescription);
        return organizationService.create(org)
                .compose(savedOrg -> {
                    admin.setOrganizationId(savedOrg.getId());
                    return identityService.createUser(admin, password)
                            .compose(savedAdmin -> {
                                savedOrg.setCreatedBy(savedAdmin.getId());
                                // the creator administers the organization: a binding of the organization admin
                                // role on the organization, which reaches everything inside it
                                return organizationService.save(savedOrg)
                                        .compose(v -> relationships.bind(AuthzStoreService.PLATFORM,
                                                                         AuthzUtil.ORGANIZATION_ADMIN_ROLE,
                                                                         AuthzUtil.object(AuthzUtil.USER_TYPE, savedAdmin.getId()),
                                                                         AuthzUtil.object(AuthzUtil.ORGANIZATION_TYPE, savedOrg.getId())))
                                        .map(savedAdmin);
                            });
                });
    }

    /**
     * Builds an unsaved {@link UserParticipantIdentity} carrying the pending record's identity. Id, dates,
     * the enabled flag and the credential are applied by {@link ParticipantIdentityService#createUser}.
     */
    private UserParticipantIdentity newUser(PendingSignUp pending) {
        UserParticipantIdentity user = new UserParticipantIdentity();
        user.setEmail(pending.getEmail())
            .setDisplayName(pending.getDisplayName())
            .setAuthType(pending.getAuthType());
        if (pending.getAuthType() == AuthType.OIDC) {
            user.setOidcSubject(pending.getOidcSubject())
                .setOidcConfigId(pending.getOidcConfigId());
        }
        return user;
    }
}
