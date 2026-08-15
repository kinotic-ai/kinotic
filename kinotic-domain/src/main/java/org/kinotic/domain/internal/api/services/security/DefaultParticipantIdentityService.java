package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.ParticipantIdentity;
import org.kinotic.domain.api.model.security.DelegateKind;
import org.kinotic.domain.api.model.security.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.MachineProvisionResult;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.internal.api.model.IdentityCredential;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.IdentityCredentialRepository;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class DefaultParticipantIdentityService extends AbstractCrudService<ParticipantIdentity> implements ParticipantIdentityService {

    /** Bytes of entropy for a machine client secret — generated, never user-chosen. */
    private static final int MACHINE_SECRET_BYTES = 32;

    private final ParticipantIdentityRepository identityRepository;
    private final IdentityCredentialRepository credentialRepository;
    private final ApplicationRepository applicationRepository;

    public DefaultParticipantIdentityService(ParticipantIdentityRepository repository,
                                 IdentityCredentialRepository credentialRepository,
                                 ApplicationRepository applicationRepository) {
        super(repository);
        this.identityRepository = repository;
        this.credentialRepository = credentialRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    protected Future<Void> beforeSave(ParticipantIdentity entity) {
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
                yield Future.succeededFuture();
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
    private Future<Void> enforceUniqueEmailInScope(UserParticipantIdentity entity) {
        return findByEmail(entity.getEmail(), entity.getOrganizationId(), entity.getApplicationId())
                .compose(existing -> {
                    if (existing != null && !existing.getId().equals(entity.getId())) {
                        throw new IllegalArgumentException(
                                "ParticipantIdentity with email " + entity.getEmail()
                                        + " already exists in scope "
                                        + DomainUtil.describeScope(entity.getOrganizationId(), entity.getApplicationId()));
                    }
                    return Future.succeededFuture();
                });
    }

    /**
     * Service-layer guarantee: at most one {@link DelegatingParticipantIdentity} per
     * {@code (ownerId, clientKey)}. Self-id is excluded so updating an existing delegate
     * doesn't trip on its own row.
     */
    private Future<Void> enforceUniqueClientKeyForOwner(DelegatingParticipantIdentity entity) {
        return identityRepository.findByOwnerAndClientKey(entity.getOwnerId(), entity.getClientKey())
                .compose(existing -> {
                    if (existing != null && !existing.getId().equals(entity.getId())) {
                        throw new IllegalArgumentException(
                                "DELEGATE with clientKey " + entity.getClientKey()
                                        + " already exists for owner " + entity.getOwnerId());
                    }
                    return Future.succeededFuture();
                });
    }

    /** Guards the query surface: applicationId is meaningful only inside an organization. */
    private static void requireOrgWithApp(String organizationId, String applicationId) {
        if (applicationId != null) {
            Validate.notBlank(organizationId,
                              "organizationId is required when applicationId is supplied");
        }
    }

    /**
     * Persists the hash of {@code secret} as the sole credential of the identity, replacing
     * any prior one.
     */
    private Future<IdentityCredential> saveCredential(String identityId, String secret) {
        return credentialRepository.save(new IdentityCredential()
                .setId(identityId)
                .setSecretHash(DomainUtil.hashPassword(secret)));
    }

    @Override
    public Future<UserParticipantIdentity> findByEmail(String email, String organizationId, String applicationId) {
        return identityRepository.findByEmail(email, organizationId, applicationId);
    }

    @Override
    public Future<UserParticipantIdentity> findFirstOrgUserByEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return identityRepository.findFirstOrgUserByEmail(email);
    }

    @Override
    public Future<UserParticipantIdentity> findByEmail(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return identityRepository.findByEmail(email);
    }

    @Override
    public Future<UserParticipantIdentity> findByOidcIdentity(String oidcSubject,
                                                              String oidcConfigId,
                                                              String organizationId,
                                                              String applicationId) {
        Validate.notBlank(oidcSubject, "oidcSubject cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        requireOrgWithApp(organizationId, applicationId);
        return identityRepository.findByOidcIdentity(oidcSubject, oidcConfigId, organizationId, applicationId);
    }

    @Override
    public Future<UserParticipantIdentity> findOrgUserByOidcIdentity(String oidcSubject, String oidcConfigId) {
        Validate.notBlank(oidcSubject, "oidcSubject cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        return identityRepository.findOrgUserByOidcIdentity(oidcSubject, oidcConfigId);
    }

    @Override
    public Future<Page<UserParticipantIdentity>> findUsersByScope(String organizationId, String applicationId, Pageable pageable) {
        requireOrgWithApp(organizationId, applicationId);
        return identityRepository.findUsersByScope(organizationId, applicationId, pageable);
    }

    @Override
    public Future<Page<UserParticipantIdentity>> searchUsersByScope(String searchText,
                                                                    String organizationId,
                                                                    String applicationId,
                                                                    Pageable pageable) {
        requireOrgWithApp(organizationId, applicationId);
        return identityRepository.searchUsersByScope(searchText, organizationId, applicationId, pageable);
    }

    @Override
    public Future<UserParticipantIdentity> createUser(UserParticipantIdentity user, String password) {
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
                .compose(this::save)
                .map(UserParticipantIdentity.class::cast)
                .compose(savedUser -> {
                    if (password != null) {
                        return saveCredential(savedUser.getId(), password).map(savedUser);
                    }
                    return Future.succeededFuture(savedUser);
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
    private Future<UserParticipantIdentity> applyTenantPolicy(UserParticipantIdentity user) {
        if (user.getApplicationId() == null || user.getTenantId() != null) {
            return Future.succeededFuture(user);
        }
        return applicationRepository.findById(user.getApplicationId(), user.getOrganizationId())
                .map(app -> {
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
    public Future<Void> changePassword(String identityId, String currentPassword, String newPassword) {
        Validate.notNull(identityId, "identityId cannot be null");
        Validate.notNull(currentPassword, "currentPassword cannot be null");
        Validate.notNull(newPassword, "newPassword cannot be null");

        return credentialRepository.findById(identityId)
                .compose(credential -> {
                    if (credential == null) {
                        return Future.failedFuture(
                                new IllegalArgumentException("No credential found for user " + identityId));
                    }
                    if (!DomainUtil.verifyPassword(currentPassword, credential.getSecretHash())) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Current password is incorrect"));
                    }
                    return saveCredential(identityId, newPassword).mapEmpty();
                });
    }

//    @Override
    public Future<Void> resetPassword(String identityId, String newPassword) {
        Validate.notNull(identityId, "identityId cannot be null");
        Validate.notNull(newPassword, "newPassword cannot be null");

        return saveCredential(identityId, newPassword).mapEmpty();
    }

    @Override
    public Future<DelegatingParticipantIdentity> findOrCreateDelegate(UserParticipantIdentity owner,
                                                                      DelegateKind kind,
                                                                      String clientKey,
                                                                      String displayName) {
        Validate.notNull(owner, "owner is required");
        Validate.notBlank(owner.getId(), "owner id is required");
        Validate.notNull(kind, "kind is required");
        Validate.notBlank(clientKey, "clientKey is required");
        Validate.notBlank(displayName, "displayName is required");

        return identityRepository.findByOwnerAndClientKey(owner.getId(), clientKey)
                .compose(existing -> {
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
                    return saveSync(delegate).map(DelegatingParticipantIdentity.class::cast);
                });
    }

    @Override
    public Future<Page<DelegatingParticipantIdentity>> findDelegatesByOwner(String ownerId, Pageable pageable) {
        return identityRepository.findDelegatesByOwner(ownerId, pageable);
    }

    @Override
    public Future<MachineProvisionResult> createMachine(MachineParticipantIdentity machine) {
        Validate.notNull(machine, "machine is required");

        Date now = new Date();
        machine.setCreated(now);
        machine.setEnabled(true);
        machine.setAuthType(AuthType.CLIENT_CREDENTIALS);

        // the secret's plaintext exists only here — the caller sees it once, storage keeps a hash
        String clientSecret = DomainUtil.generateUrlSafeToken(MACHINE_SECRET_BYTES);

        // sync so the console's immediate re-query lists the new machine
        return saveSync(machine)
                .map(MachineParticipantIdentity.class::cast)
                .compose(saved -> saveCredential(saved.getId(), clientSecret)
                        .map(new MachineProvisionResult(saved, clientSecret)));
    }

    @Override
    public Future<Page<MachineParticipantIdentity>> findMachinesByScope(String organizationId, String applicationId, Pageable pageable) {
        requireOrgWithApp(organizationId, applicationId);
        return identityRepository.findMachinesByScope(organizationId, applicationId, pageable);
    }

    @Override
    public Future<String> rotateMachineSecret(String machineId) {
        Validate.notBlank(machineId, "machineId is required");
        return findById(machineId)
                .compose(identity -> {
                    if (!(identity instanceof MachineParticipantIdentity)) {
                        throw new IllegalArgumentException("Machine not found.");
                    }
                    String clientSecret = DomainUtil.generateUrlSafeToken(MACHINE_SECRET_BYTES);
                    return saveCredential(identity.getId(), clientSecret).map(clientSecret);
                });
    }

    @Override
    public Future<MachineParticipantIdentity> verifyMachineCredentials(String machineId, String clientSecret) {
        Validate.notBlank(machineId, "machineId is required");
        Validate.notBlank(clientSecret, "clientSecret is required");

        return findById(machineId)
                .compose(identity -> {
                    // one generic failure for every miss — no oracle for which check failed
                    if (!(identity instanceof MachineParticipantIdentity machine) || !machine.isEnabled()) {
                        throw new IllegalArgumentException("Invalid client credentials");
                    }
                    return credentialRepository.findById(machine.getId())
                            .map(credential -> {
                                if (credential == null
                                        || !DomainUtil.verifyPassword(clientSecret, credential.getSecretHash())) {
                                    throw new IllegalArgumentException("Invalid client credentials");
                                }
                                return machine;
                            });
                });
    }

    @Override
    protected Future<Void> beforeDelete(String id) {
        // Cascade the IdentityCredential. Credential lookups are by id (realtime GETs), so the
        // credential delete never needs to wait for search visibility.
        // Delegates owned by the deleted identity go with it — a delegate must not outlive
        // the authority it wields (resolveParticipant would reject it anyway; this is hygiene).
        return credentialRepository.deleteById(id)
                .compose(v -> identityRepository.findDelegatesByOwner(id, Pageable.create(0, 1000, Sort.by("created"))))
                .compose(delegates -> {
                    List<Future<Void>> deletes = delegates.getContent().stream()
                            .map(delegate -> deleteById(delegate.getId()))
                            .toList();
                    return Future.all(deletes).mapEmpty();
                });
    }

}
