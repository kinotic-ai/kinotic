package org.kinotic.domain.internal.api.services.iam;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.internal.api.model.IamCredential;
import org.kinotic.domain.internal.api.repositories.IamCredentialRepository;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultIamUserService extends AbstractCrudService<IamUser> implements IamUserService {

    private final IamUserRepository iamUserRepository;
    private final IamCredentialRepository credentialRepository;

    public DefaultIamUserService(IamUserRepository repository,
                                 IamCredentialRepository credentialRepository) {
        super(repository);
        this.iamUserRepository = repository;
        this.credentialRepository = credentialRepository;
    }

    @Override
    public CompletableFuture<IamUser> save(IamUser entity) {
        Validate.notNull(entity.getEmail(), "IamUser email cannot be null");
        validateScopeFields(entity);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdated(new Date());
        return enforceUniqueEmailInScope(entity).thenCompose(v -> super.save(entity));
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
    private void validateScopeFields(IamUser entity) {
        if (entity.getApplicationId() != null) {
            Validate.notBlank(entity.getOrganizationId(),
                              "IamUser organizationId is required when applicationId is set");
        } else if (entity.getTenantId() != null) {
            throw new IllegalArgumentException(
                    "IamUser tenantId must be null when applicationId is null (tenantId is "
                    + "meaningful only for APPLICATION-scope users)");
        }
    }

    /**
     * Service-layer guarantee: at most one {@link IamUser} per
     * {@code (email, organizationId, applicationId)}. Self-id is excluded so updating an
     * existing user doesn't trip on its own row.
     */
    private CompletableFuture<Void> enforceUniqueEmailInScope(IamUser entity) {
        AuthScopeType type;
        String scopeId;
        if (entity.getApplicationId() != null) {
            type = AuthScopeType.APPLICATION;
            scopeId = entity.getApplicationId();
        } else if (entity.getOrganizationId() != null) {
            type = AuthScopeType.ORGANIZATION;
            scopeId = entity.getOrganizationId();
        } else {
            type = AuthScopeType.SYSTEM;
            scopeId = null;
        }
        return findByEmailAndScope(entity.getEmail(), type.name(), scopeId)
                .thenAccept(existing -> {
                    if (existing != null && !existing.getId().equals(entity.getId())) {
                        throw new IllegalArgumentException(
                                "IamUser with email " + entity.getEmail()
                                        + " already exists in scope " + type
                                        + (scopeId != null ? "/" + scopeId : ""));
                    }
                });
    }

    @Override
    public CompletableFuture<IamUser> findByEmailAndScope(String email, String authScopeType, String authScopeId) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(authScopeType, "authScopeType cannot be blank");
        return iamUserRepository.findByEmailAndScope(email, authScopeType, authScopeId);
    }

    @Override
    public CompletableFuture<IamUser> findFirstByEmailInScopeType(String email, String authScopeType) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(authScopeType, "authScopeType cannot be blank");
        return iamUserRepository.findFirstByEmailInScopeType(email, authScopeType);
    }

    @Override
    public CompletableFuture<IamUser> findByEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return iamUserRepository.findByEmail(email);
    }

    @Override
    public CompletableFuture<IamUser> findByOidcIdentityAndScope(String oidcSubject,
                                                                 String oidcConfigId,
                                                                 String authScopeType,
                                                                 String authScopeId) {
        Validate.notBlank(oidcSubject, "oidcSubject cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(authScopeType, "authScopeType cannot be blank");
        return iamUserRepository.findByOidcIdentityAndScope(oidcSubject, oidcConfigId, authScopeType, authScopeId);
    }

    @Override
    public CompletableFuture<java.util.List<IamUser>> findByOidcIdentity(String oidcSubject, String oidcConfigId) {
        Validate.notBlank(oidcSubject, "oidcSubject cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        return iamUserRepository.findByOidcIdentity(oidcSubject, oidcConfigId);
    }

    @Override
    public CompletableFuture<IamUser> createUser(IamUser user, String password) {
        Validate.notNull(user.getEmail(), "IamUser email cannot be null");
        validateScopeFields(user);

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

        return save(user)
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

//    @Override // commented off the interface — kept for the eventual user-management UI
    public CompletableFuture<Void> changePassword(String userId, String currentPassword, String newPassword) {
        Validate.notNull(userId, "userId cannot be null");
        Validate.notNull(currentPassword, "currentPassword cannot be null");
        Validate.notNull(newPassword, "newPassword cannot be null");

        return credentialRepository.findById(userId)
                .thenCompose(credential -> {
                    if (credential == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("No credential found for user " + userId));
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
    public CompletableFuture<Void> resetPassword(String userId, String newPassword) {
        Validate.notNull(userId, "userId cannot be null");
        Validate.notNull(newPassword, "newPassword cannot be null");

        IamCredential credential = new IamCredential()
                .setId(userId)
                .setPasswordHash(DomainUtil.hashPassword(newPassword));
        return credentialRepository.save(credential).thenApply(c -> null);
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return credentialRepository.deleteById(id)
                .thenCompose(v -> super.deleteById(id));
    }

}
