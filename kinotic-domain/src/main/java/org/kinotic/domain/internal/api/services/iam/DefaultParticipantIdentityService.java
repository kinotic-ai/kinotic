package org.kinotic.domain.internal.api.services.iam;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.ParticipantIdentity;
import org.kinotic.domain.api.model.iam.DelegateKind;
import org.kinotic.domain.api.model.iam.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.iam.MachineParticipantIdentity;
import org.kinotic.domain.api.model.iam.MachineProvisionResult;
import org.kinotic.domain.api.model.iam.UserParticipantIdentity;
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
        validateScopeFields(entity);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdated(new Date());

        return switch (entity) {
            case DelegatingParticipantIdentity delegate -> {
                Validate.notBlank(delegate.getOwnerId(), "DELEGATE ownerId is required");
                Validate.notBlank(delegate.getClientKey(), "DELEGATE clientKey is required");
                Validate.notNull(delegate.getDelegateKind(), "DELEGATE delegateKind is required");
                Validate.isTrue(delegate.getAuthType() == AuthType.DELEGATED,
                                "DELEGATE authType must be DELEGATED");
                yield enforceUniqueClientKeyForOwner(delegate);
            }
            case UserParticipantIdentity user -> {
                Validate.notNull(user.getEmail(), "UserParticipantIdentity email cannot be null");
                // Canonical form at the single write chokepoint; lookups normalize in the repository.
                user.setEmail(DomainUtil.normalizeEmail(user.getEmail()));
                yield enforceUniqueEmailInScope(user);
            }
            case MachineParticipantIdentity machine -> {
                Validate.notBlank(machine.getDisplayName(), "MACHINE displayName is required");
                Validate.isTrue(machine.getAuthType() == AuthType.CLIENT_CREDENTIALS,
                                "MACHINE authType must be CLIENT_CREDENTIALS");
                // the machine's id is its client_id, so uniqueness is the id's own
                yield CompletableFuture.completedFuture(null);
            }
        };
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
     * Service-layer guarantee: at most one {@link UserParticipantIdentity} per
     * {@code (email, organizationId, applicationId)}. Self-id is excluded so updating an
     * existing user doesn't trip on its own row.
     */
    private CompletableFuture<Void> enforceUniqueEmailInScope(UserParticipantIdentity entity) {
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
     * Service-layer guarantee: at most one {@link DelegatingParticipantIdentity} per
     * {@code (ownerId, clientKey)}. Self-id is excluded so updating an existing delegate
     * doesn't trip on its own row.
     */
    private CompletableFuture<Void> enforceUniqueClientKeyForOwner(DelegatingParticipantIdentity entity) {
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
    public CompletableFuture<UserParticipantIdentity> findByEmail(String email, String organizationId, String applicationId) {
        return identityRepository.findByEmail(email, organizationId, applicationId);
    }

    @Override
    public CompletableFuture<UserParticipantIdentity> findFirstOrgUserByEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return identityRepository.findFirstOrgUserByEmail(email);
    }

    @Override
    public CompletableFuture<UserParticipantIdentity> findByEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return identityRepository.findByEmail(email);
    }

    @Override
    public CompletableFuture<UserParticipantIdentity> findByOidcIdentity(String oidcSubject,
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
    public CompletableFuture<UserParticipantIdentity> findOrgUserByOidcIdentity(String oidcSubject, String oidcConfigId) {
        Validate.notBlank(oidcSubject, "oidcSubject cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        return identityRepository.findOrgUserByOidcIdentity(oidcSubject, oidcConfigId);
    }

    @Override
    public CompletableFuture<Page<UserParticipantIdentity>> findUsersByScope(String organizationId, String applicationId, Pageable pageable) {
        if (applicationId != null) {
            Validate.notBlank(organizationId,
                              "organizationId is required when applicationId is supplied");
        }
        return identityRepository.findUsersByScope(organizationId, applicationId, pageable);
    }

    @Override
    public CompletableFuture<Page<UserParticipantIdentity>> searchUsersByScope(String searchText,
                                                          String organizationId,
                                                          String applicationId,
                                                          Pageable pageable) {
        if (applicationId != null) {
            Validate.notBlank(organizationId,
                              "organizationId is required when applicationId is supplied");
        }
        return identityRepository.searchUsersByScope(searchText, organizationId, applicationId, pageable);
    }

    @Override
    public CompletableFuture<UserParticipantIdentity> createUser(UserParticipantIdentity user, String password) {
        Validate.notNull(user.getEmail(), "UserParticipantIdentity email cannot be null");
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

        return applyTenantPolicy(user)
                .thenCompose(this::save)
                .thenApply(UserParticipantIdentity.class::cast)
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
    private CompletableFuture<UserParticipantIdentity> applyTenantPolicy(UserParticipantIdentity user) {
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
    public CompletableFuture<DelegatingParticipantIdentity> findOrCreateDelegate(UserParticipantIdentity owner,
                                                                                 DelegateKind kind,
                                                                                 String clientKey,
                                                                                 String displayName) {
        Validate.notNull(owner, "owner is required");
        Validate.notBlank(owner.getId(), "owner id is required");
        Validate.notNull(kind, "kind is required");
        Validate.notBlank(clientKey, "clientKey is required");
        Validate.notBlank(displayName, "displayName is required");

        return identityRepository.findByOwnerAndClientKey(owner.getId(), clientKey)
                .thenCompose(existing -> {
                    DelegatingParticipantIdentity delegate;
                    if (existing != null) {
                        delegate = existing;
                    } else {
                        delegate = new DelegatingParticipantIdentity();
                        delegate.setOwnerId(owner.getId())
                                .setClientKey(clientKey)
                                .setDelegateKind(kind)
                                .setAuthType(AuthType.DELEGATED)
                                // the delegate wields the owner's authority, so it lives at the
                                // owner's exact scope, tenant included
                                .setOrganizationId(owner.getOrganizationId())
                                .setApplicationId(owner.getApplicationId())
                                .setTenantId(owner.getTenantId())
                                .setCreated(new Date());
                    }
                    delegate.setDisplayName(displayName);
                    delegate.setEnabled(true);
                    // sync so the (ownerId, clientKey) uniqueness search sees this write before
                    // the next approval of the same client can run
                    return saveSync(delegate).thenApply(DelegatingParticipantIdentity.class::cast);
                });
    }

    @Override
    public CompletableFuture<Page<DelegatingParticipantIdentity>> findDelegatesByOwner(String ownerId, Pageable pageable) {
        return identityRepository.findDelegatesByOwner(ownerId, pageable);
    }

    @Override
    public CompletableFuture<MachineProvisionResult> createMachine(MachineParticipantIdentity machine) {
        Validate.notNull(machine, "machine is required");

        Date now = new Date();
        machine.setCreated(now);
        machine.setEnabled(true);
        machine.setAuthType(AuthType.CLIENT_CREDENTIALS);

        // 32 bytes of entropy — the secret is generated, never user-chosen, and shown only here
        String clientSecret = DomainUtil.generateUrlSafeToken(32);

        return save(machine)
                .thenApply(MachineParticipantIdentity.class::cast)
                .thenCompose(saved -> {
                    IamCredential credential = new IamCredential()
                            .setId(saved.getId())
                            .setPasswordHash(DomainUtil.hashPassword(clientSecret));
                    return credentialRepository.save(credential)
                                               .thenApply(c -> new MachineProvisionResult(saved, clientSecret));
                });
    }

    @Override
    public CompletableFuture<MachineParticipantIdentity> verifyMachineCredentials(String machineId, String clientSecret) {
        Validate.notBlank(machineId, "machineId is required");
        Validate.notBlank(clientSecret, "clientSecret is required");

        return findById(machineId)
                .thenCompose(identity -> {
                    // one generic failure for every miss — no oracle for which check failed
                    if (!(identity instanceof MachineParticipantIdentity machine) || !machine.isEnabled()) {
                        throw new IllegalArgumentException("Invalid client credentials");
                    }
                    return credentialRepository.findById(machine.getId())
                            .thenApply(credential -> {
                                if (credential == null
                                        || !DomainUtil.verifyPassword(clientSecret, credential.getPasswordHash())) {
                                    throw new IllegalArgumentException("Invalid client credentials");
                                }
                                return machine;
                            });
                });
    }

    @Override
    protected CompletableFuture<Void> beforeDelete(String id) {
        // Cascade the IamCredential. Credential lookups are by id (realtime GETs), so the
        // credential delete never needs to wait for search visibility.
        // Delegates owned by the deleted identity go with it — a delegate must not outlive
        // the authority it wields (resolveParticipant would reject it anyway; this is hygiene).
        return credentialRepository.deleteById(id)
                .thenCompose(v -> identityRepository.findDelegatesByOwner(id, Pageable.create(0, 1000, Sort.by("created"))))
                .thenCompose(delegates -> {
                    CompletableFuture<?>[] deletes = delegates.getContent().stream()
                            .map(delegate -> deleteById(delegate.getId()))
                            .toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(deletes);
                });
    }

}
