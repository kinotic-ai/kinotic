package org.kinotic.domain.internal.api.services.iam;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.ParticipantIdentity;
import org.kinotic.domain.api.model.iam.ParticipantIdentityType;
import org.kinotic.domain.api.services.iam.ParticipantIdentityService;
import org.kinotic.domain.internal.api.model.IamCredential;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.IamCredentialRepository;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultParticipantIdentityService extends AbstractCrudService<ParticipantIdentity> implements ParticipantIdentityService {

    private final ParticipantIdentityRepository identityRepository;
    private final IamCredentialRepository credentialRepository;
    private final ApplicationRepository applicationRepository;

    public DefaultParticipantIdentityService(ParticipantIdentityRepository repository,
                                 IamCredentialRepository credentialRepository,
                                 ApplicationRepository applicationRepository) {
        super(repository);
        this.identityRepository = repository;
        this.credentialRepository = credentialRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    protected CompletableFuture<Void> beforeSave(ParticipantIdentity entity) {
        Validate.notNull(entity.getType(), "ParticipantIdentity type cannot be null");
        validateScopeFields(entity);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdated(new Date());

        CompletableFuture<Void> ret;
        if (entity.getType() == ParticipantIdentityType.DELEGATE) {
            validateDelegateFields(entity);
            ret = enforceUniqueClientKeyForOwner(entity);
        } else {
            validateUserFields(entity);
            // Canonical form at the single write chokepoint; lookups normalize in the repository.
            entity.setEmail(DomainUtil.normalizeEmail(entity.getEmail()));
            ret = enforceUniqueEmailInScope(entity);
        }
        return ret;
    }

    private static void validateUserFields(ParticipantIdentity entity) {
        Validate.notNull(entity.getEmail(), "ParticipantIdentity email cannot be null");
        Validate.isTrue(entity.getOwnerId() == null && entity.getClientKey() == null
                                && entity.getDelegateKind() == null,
                        "ownerId, clientKey, and delegateKind are DELEGATE-only fields");
    }

    private static void validateDelegateFields(ParticipantIdentity entity) {
        Validate.notBlank(entity.getOwnerId(), "DELEGATE ownerId is required");
        Validate.notBlank(entity.getClientKey(), "DELEGATE clientKey is required");
        Validate.notNull(entity.getDelegateKind(), "DELEGATE delegateKind is required");
        Validate.isTrue(entity.getAuthType() == AuthType.DELEGATED,
                        "DELEGATE authType must be DELEGATED");
        Validate.isTrue(entity.getEmail() == null, "DELEGATE email must be null");
        Validate.isTrue(entity.getOidcSubject() == null && entity.getOidcConfigId() == null,
                        "oidcSubject and oidcConfigId are USER-only fields");
    }

    /**
     * Scope shape is encoded structurally — every save must conform to one of the three
     * valid combinations:
     * <ul>
     *   <li>SYSTEM: both {@code organizationId} and {@code applicationId} null, {@code tenantId} null</li>
     *   <li>ORGANIZATION: {@code organizationId} set, {@code applicationId} null, {@code tenantId} null</li>
     *   <li>APPLICATION: {@code organizationId} set, {@code applicationId} set; {@code tenantId} optional</li>
     * </ul>
     */
    private void validateScopeFields(ParticipantIdentity entity) {
        if (entity.getApplicationId() != null) {
            Validate.notBlank(entity.getOrganizationId(),
                              "ParticipantIdentity organizationId is required when applicationId is set");
        } else if (entity.getTenantId() != null) {
            throw new IllegalArgumentException(
                    "ParticipantIdentity tenantId must be null when applicationId is null (tenantId is "
                    + "meaningful only for APPLICATION-scope users)");
        }
    }

    /**
     * Service-layer guarantee: at most one {@link ParticipantIdentity} per
     * {@code (email, organizationId, applicationId)}. Self-id is excluded so updating an
     * existing user doesn't trip on its own row.
     */
    private CompletableFuture<Void> enforceUniqueEmailInScope(ParticipantIdentity entity) {
        return findByEmail(entity.getEmail(), entity.getOrganizationId(), entity.getApplicationId())
                .thenAccept(existing -> {
                    if (existing != null && !existing.getId().equals(entity.getId())) {
                        throw new IllegalArgumentException(
                                "ParticipantIdentity with email " + entity.getEmail()
                                        + " already exists in scope " + describeScope(entity));
                    }
                });
    }

    /**
     * Service-layer guarantee: at most one DELEGATE per {@code (ownerId, clientKey)}.
     * Self-id is excluded so updating an existing delegate doesn't trip on its own row.
     */
    private CompletableFuture<Void> enforceUniqueClientKeyForOwner(ParticipantIdentity entity) {
        return identityRepository.findByOwnerAndClientKey(entity.getOwnerId(), entity.getClientKey())
                .thenAccept(existing -> {
                    if (existing != null && !existing.getId().equals(entity.getId())) {
                        throw new IllegalArgumentException(
                                "DELEGATE with clientKey " + entity.getClientKey()
                                        + " already exists for owner " + entity.getOwnerId());
                    }
                });
    }

    private static String describeScope(ParticipantIdentity user) {
        if (user.getOrganizationId() == null) return "SYSTEM";
        if (user.getApplicationId() == null) return "ORGANIZATION/" + user.getOrganizationId();
        return "APPLICATION/" + user.getOrganizationId() + "/" + user.getApplicationId();
    }

    @Override
    public CompletableFuture<ParticipantIdentity> findByEmail(String email, String organizationId, String applicationId) {
        return identityRepository.findByEmail(email, organizationId, applicationId);
    }

    @Override
    public CompletableFuture<ParticipantIdentity> findFirstOrgUserByEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return identityRepository.findFirstOrgUserByEmail(email);
    }

    @Override
    public CompletableFuture<ParticipantIdentity> findByEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return identityRepository.findByEmail(email);
    }

    @Override
    public CompletableFuture<ParticipantIdentity> findByOidcIdentity(String oidcSubject,
                                                         String oidcConfigId,
                                                         String organizationId,
                                                         String applicationId) {
        Validate.notBlank(oidcSubject, "oidcSubject cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        if (applicationId != null) {
            Validate.notBlank(organizationId,
                              "organizationId is required when applicationId is supplied");
        }
        return identityRepository.findByOidcIdentity(oidcSubject, oidcConfigId, organizationId, applicationId);
    }

    @Override
    public CompletableFuture<ParticipantIdentity> findOrgUserByOidcIdentity(String oidcSubject, String oidcConfigId) {
        Validate.notBlank(oidcSubject, "oidcSubject cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        return identityRepository.findOrgUserByOidcIdentity(oidcSubject, oidcConfigId);
    }

    @Override
    public CompletableFuture<Page<ParticipantIdentity>> findByScope(String organizationId, String applicationId, Pageable pageable) {
        if (applicationId != null) {
            Validate.notBlank(organizationId,
                              "organizationId is required when applicationId is supplied");
        }
        return identityRepository.findByScope(organizationId, applicationId, pageable);
    }

    @Override
    public CompletableFuture<Page<ParticipantIdentity>> searchByScope(String searchText,
                                                          String organizationId,
                                                          String applicationId,
                                                          Pageable pageable) {
        if (applicationId != null) {
            Validate.notBlank(organizationId,
                              "organizationId is required when applicationId is supplied");
        }
        return identityRepository.searchByScope(searchText, organizationId, applicationId, pageable);
    }

    @Override
    public CompletableFuture<ParticipantIdentity> createUser(ParticipantIdentity user, String password) {
        Validate.notNull(user.getEmail(), "ParticipantIdentity email cannot be null");
        validateScopeFields(user);
        user.setType(ParticipantIdentityType.USER);

        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }

        Date now = new Date();
        user.setCreated(now);
        user.setUpdated(now);
        user.setEnabled(true);

        if (user.getAuthType() == null) {
            user.setAuthType(password != null ? AuthType.LOCAL : AuthType.OIDC);
        }

        return applyTenantPolicy(user)
                .thenCompose(this::save)
                .thenCompose(savedUser -> {
                    if (password != null) {
                        IamCredential credential = new IamCredential()
                                .setId(savedUser.getId())
                                .setPasswordHash(DomainUtil.hashPassword(password));
                        return credentialRepository.save(credential).thenApply(c -> savedUser);
                    }
                    return CompletableFuture.completedFuture(savedUser);
                });
    }

    /**
     * Applies the owning application's tenant policy to a new APPLICATION-scope user: when the
     * app has {@code tenantPerUser} enabled and no explicit tenantId was supplied, a fresh UUID
     * becomes the user's tenantId. Deliberately NOT the user's id — the tenantId is an ES
     * routing key and part of the immutable _id of every SHARED entity the user writes, while
     * createUser accepts caller-supplied ids of any shape; a dedicated UUID keeps tenant
     * identity decoupled from id semantics.
     */
    private CompletableFuture<ParticipantIdentity> applyTenantPolicy(ParticipantIdentity user) {
        if (user.getApplicationId() == null || user.getTenantId() != null) {
            return CompletableFuture.completedFuture(user);
        }
        return applicationRepository.findById(user.getApplicationId(), user.getOrganizationId())
                .thenApply(app -> {
                    if (app == null) {
                        throw new IllegalArgumentException(
                                "Application " + user.getApplicationId() + " not found in organization "
                                + user.getOrganizationId());
                    }
                    if (app.isTenantPerUser()) {
                        user.setTenantId(UUID.randomUUID().toString());
                    }
                    return user;
                });
    }

//    @Override // commented off the interface — kept for the eventual user-management UI
    public CompletableFuture<Void> changePassword(String identityId, String currentPassword, String newPassword) {
        Validate.notNull(identityId, "identityId cannot be null");
        Validate.notNull(currentPassword, "currentPassword cannot be null");
        Validate.notNull(newPassword, "newPassword cannot be null");

        return credentialRepository.findById(identityId)
                .thenCompose(credential -> {
                    if (credential == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("No credential found for user " + identityId));
                    }
                    if (!DomainUtil.verifyPassword(currentPassword, credential.getPasswordHash())) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Current password is incorrect"));
                    }
                    credential.setPasswordHash(DomainUtil.hashPassword(newPassword));
                    return credentialRepository.save(credential).thenApply(c -> (Void) null);
                });
    }

//    @Override
    public CompletableFuture<Void> resetPassword(String identityId, String newPassword) {
        Validate.notNull(identityId, "identityId cannot be null");
        Validate.notNull(newPassword, "newPassword cannot be null");

        IamCredential credential = new IamCredential()
                .setId(identityId)
                .setPasswordHash(DomainUtil.hashPassword(newPassword));
        return credentialRepository.save(credential).thenApply(c -> null);
    }

    @Override
    protected CompletableFuture<Void> beforeDelete(String id) {
        // Cascade the IamCredential. Credential lookups are by id (realtime GETs), so the
        // credential delete never needs to wait for search visibility.
        return credentialRepository.deleteById(id);
    }

}
