package org.kinotic.domain.internal.api.services.iam;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.security.DefaultApplicationParticipant;
import org.kinotic.domain.api.security.DefaultOrganizationParticipant;
import org.kinotic.domain.api.security.DefaultSystemParticipant;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.internal.api.model.IamCredential;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.IamCredentialRepository;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultIamUserService extends AbstractCrudService<IamUser> implements IamUserService {

    private final IamUserRepository iamUserRepository;
    private final IamCredentialRepository credentialRepository;
    private final ApplicationRepository applicationRepository;

    public DefaultIamUserService(IamUserRepository repository,
                                 IamCredentialRepository credentialRepository,
                                 ApplicationRepository applicationRepository) {
        super(repository);
        this.iamUserRepository = repository;
        this.credentialRepository = credentialRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    public CompletableFuture<IamUser> save(IamUser entity) {
        Validate.notNull(entity.getEmail(), "IamUser email cannot be null");
        Validate.notNull(entity.getAuthScopeType(), "IamUser authScopeType cannot be null");
        Validate.notNull(entity.getAuthScopeId(), "IamUser authScopeId cannot be null");
        // tenantId is meaningful only for APPLICATION-scoped users; SYSTEM/ORGANIZATION identities
        // are not tenants and must not carry one.
        if ("APPLICATION".equals(entity.getAuthScopeType())) {
            Validate.notBlank(entity.getTenantId(),
                              "IamUser tenantId is required for APPLICATION-scoped users");
        } else if (entity.getTenantId() != null) {
            throw new IllegalArgumentException(
                    "IamUser tenantId must be null for " + entity.getAuthScopeType() + "-scoped users");
        }
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdated(new Date());
        return enforceUniqueEmailInScope(entity).thenCompose(v -> super.save(entity));
    }

    /**
     * Service-layer guarantee: at most one {@link IamUser} per
     * {@code (email, authScopeType, authScopeId)}. Self-id is excluded so updating an
     * existing user doesn't trip on its own row.
     */
    private CompletableFuture<Void> enforceUniqueEmailInScope(IamUser entity) {
        return findByEmailAndScope(entity.getEmail(), entity.getAuthScopeType(), entity.getAuthScopeId())
                .thenAccept(existing -> {
                    if (existing != null && !existing.getId().equals(entity.getId())) {
                        throw new IllegalArgumentException(
                                "IamUser with email " + entity.getEmail()
                                        + " already exists in scope "
                                        + entity.getAuthScopeType() + "/" + entity.getAuthScopeId());
                    }
                });
    }

    @Override
    public CompletableFuture<IamUser> findByEmailAndScope(String email, String authScopeType, String authScopeId) {
        Validate.notNull(authScopeId, "authScopeId cannot be null");
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
        Validate.notNull(authScopeId, "authScopeId cannot be null");
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
        Validate.notNull(user.getAuthScopeType(), "IamUser authScopeType cannot be null");

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

    @Override
    public CompletableFuture<Participant> createParticipant(IamUser user) {
        return switch (AuthScopeType.valueOf(user.getAuthScopeType())) {
            case SYSTEM -> CompletableFuture.completedFuture(
                    DefaultSystemParticipant.builder()
                                            .id(user.getId())
                                            .metadata(metadataFor(user))
                                            .roles(List.of())
                                            .build());
            case ORGANIZATION -> CompletableFuture.completedFuture(
                    DefaultOrganizationParticipant.builder()
                                                  .id(user.getId())
                                                  .organizationId(user.getAuthScopeId())
                                                  .metadata(metadataFor(user))
                                                  .roles(List.of())
                                                  .build());
            case APPLICATION -> applicationRepository.findByAppId(user.getAuthScopeId())
                    .thenApply(application -> {
                        if (application == null) {
                            throw new IllegalStateException(
                                    "Cannot build ApplicationParticipant for IamUser '" + user.getId()
                                    + "': no Application found for appId '" + user.getAuthScopeId() + "'.");
                        }
                        return DefaultApplicationParticipant.builder()
                                                            .id(user.getId())
                                                            .organizationId(application.getOrganizationId())
                                                            .applicationId(user.getAuthScopeId())
                                                            .tenantId(user.getTenantId())
                                                            .metadata(metadataFor(user))
                                                            .roles(List.of())
                                                            .build();
                    });
        };
    }

    private static Map<String, String> metadataFor(IamUser user) {
        return Map.of(
                ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                "email", user.getEmail(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail(),
                "authType", user.getAuthType().name()
        );
    }

}
