package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.DelegateKind;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.Membership;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.exceptions.AccountDisabledException;
import org.kinotic.domain.api.services.security.MembershipService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.internal.api.model.IdentityCredential;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.IdentityCredentialRepository;
import org.kinotic.domain.internal.api.repositories.MembershipRepository;
import org.kinotic.domain.internal.api.repositories.OidcBindingRepository;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class DefaultParticipantIdentityService extends AbstractCrudService<ParticipantIdentity> implements ParticipantIdentityService {

    /** Bytes of entropy for a machine client secret — generated, never user-chosen. */
    private static final int MACHINE_SECRET_BYTES = 32;

    private final ParticipantIdentityRepository identityRepository;
    private final IdentityCredentialRepository credentialRepository;
    private final OidcBindingRepository bindingRepository;
    private final MembershipRepository membershipRepository;
    private final ApplicationRepository applicationRepository;
    private final MembershipService membershipService;

    public DefaultParticipantIdentityService(ParticipantIdentityRepository repository,
                                             IdentityCredentialRepository credentialRepository,
                                             OidcBindingRepository bindingRepository,
                                             MembershipRepository membershipRepository,
                                             ApplicationRepository applicationRepository,
                                             MembershipService membershipService) {
        super(repository);
        this.identityRepository = repository;
        this.credentialRepository = credentialRepository;
        this.bindingRepository = bindingRepository;
        this.membershipRepository = membershipRepository;
        this.applicationRepository = applicationRepository;
        this.membershipService = membershipService;
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
                Validate.isTrue(user.getAuthType() == null, "A user has no authType; its sign-in methods are its credential and bindings");
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
     *   <li>both {@code organizationId} and {@code applicationId} null, {@code tenantId} null: an account, or a platform machine or delegate</li>
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
     * {@code (ownerId, clientKey)} within one scope, since an account approves the same client once per
     * membership. Self-id is excluded so updating an existing delegate doesn't trip on its own row.
     */
    private Future<Void> enforceUniqueClientKeyForOwner(DelegatingParticipantIdentity entity) {
        return identityRepository.findByOwnerAndClientKey(entity.getOwnerId(), entity.getClientKey(),
                                                          entity.getOrganizationId(), entity.getApplicationId())
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
    public Future<UserParticipantIdentity> findByOidcIdentity(String oidcConfigId,
                                                              String subject,
                                                              String organizationId,
                                                              String applicationId) {
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(subject, "subject cannot be blank");
        requireOrgWithApp(organizationId, applicationId);
        return bindingRepository.findById(OidcBinding.idOf(oidcConfigId, subject, organizationId, applicationId))
                                .compose(binding -> binding == null
                                        ? Future.succeededFuture()
                                        : identityRepository.findById(binding.getIdentityId()))
                                .map(identity -> identity instanceof UserParticipantIdentity user ? user : null);
    }

    @Override
    public Future<OidcBinding> bindOidcIdentity(String identityId, String oidcConfigId, String subject) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(subject, "subject cannot be blank");
        return identityRepository.findById(identityId).compose(identity -> {
            Validate.isTrue(identity instanceof UserParticipantIdentity, "User not found: %s", identityId);
            return bind((UserParticipantIdentity) identity, oidcConfigId, subject);
        });
    }

    // the binding's id carries the user's scope, so the same provider identity binds to one account and to
    // one user of each application
    private Future<OidcBinding> bind(UserParticipantIdentity user, String oidcConfigId, String subject) {
        String bindingId = OidcBinding.idOf(oidcConfigId, subject, user.getOrganizationId(), user.getApplicationId());
        return bindingRepository.findById(bindingId).compose(existing -> {
            Future<OidcBinding> ret;
            if (existing != null) {
                Validate.isTrue(user.getId().equals(existing.getIdentityId()), "This identity already signs in another user");
                ret = Future.succeededFuture(existing);
            } else {
                // sync so the profile's list of sign-in methods shows the binding at once
                ret = bindingRepository.saveSync(new OidcBinding().setId(bindingId)
                                                                  .setIdentityId(user.getId())
                                                                  .setOrganizationId(user.getOrganizationId())
                                                                  .setApplicationId(user.getApplicationId())
                                                                  .setOidcConfigId(oidcConfigId)
                                                                  .setSubject(subject)
                                                                  .setCreated(new Date()));
            }
            return ret;
        });
    }

    @Override
    public Future<List<OidcBinding>> findOidcBindings(String identityId) {
        return bindingRepository.findByIdentity(identityId);
    }

    @Override
    public Future<Void> removeOidcBinding(String identityId, String bindingId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        Validate.notBlank(bindingId, "bindingId cannot be blank");
        return bindingRepository.findById(bindingId).compose(binding -> {
            Validate.isTrue(binding != null && identityId.equals(binding.getIdentityId()), "Sign-in method not found.");
            return bindingRepository.deleteByIdSync(bindingId);
        });
    }

    @Override
    public Future<Boolean> hasPassword(String identityId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return credentialRepository.findById(identityId).map(Objects::nonNull);
    }

    @Override
    public Future<Void> setPassword(String identityId, String password) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        Validate.notBlank(password, "password cannot be blank");
        return saveCredential(identityId, password).mapEmpty();
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

    @Override
    public Future<UserParticipantIdentity> createOidcUser(UserParticipantIdentity user, String oidcConfigId, String subject) {
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(subject, "subject cannot be blank");
        // checked before the user exists, so a bound identity leaves no user behind that no method signs in
        return bindingRepository.findById(OidcBinding.idOf(oidcConfigId, subject, user.getOrganizationId(), user.getApplicationId()))
                                .compose(existing -> {
                                    Validate.isTrue(existing == null, "This identity already signs in another user");
                                    return createUser(user, null);
                                })
                                .compose(created -> bind(created, oidcConfigId, subject).map(created));
    }

    @Override
    public Future<Participant> resolveParticipant(String identityId, String organizationId, String applicationId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return identityRepository.findById(identityId).compose(identity -> resolveParticipant(identity, organizationId, applicationId));
    }

    @Override
    public Future<Participant> resolveParticipant(ParticipantIdentity identity, String organizationId, String applicationId) {
        requireOrgWithApp(organizationId, applicationId);
        Future<Participant> ret;
        if (identity == null || !identity.isEnabled()) {
            ret = Future.succeededFuture();
        } else if (identity instanceof UserParticipantIdentity user && user.isAccount()) {
            // a login answers a refused account the way it answers a wrong password, so the reason stays here
            ret = applicationId != null
                    ? Future.succeededFuture()
                    : membershipService.resolveMember(user, organizationId)
                                       .recover(err -> err instanceof AccountDisabledException ? Future.succeededFuture() : Future.failedFuture(err));
        } else if (!Objects.equals(identity.getOrganizationId(), organizationId)
                || !Objects.equals(identity.getApplicationId(), applicationId)) {
            ret = Future.succeededFuture();
        } else if (identity instanceof DelegatingParticipantIdentity delegate) {
            // a delegate wields its owner's authority in the delegate's scope, so revoking the owner there
            // revokes every delegate on the next request, with no cascade to miss
            ret = resolveParticipant(delegate.getOwnerId(), organizationId, applicationId)
                    .map(owner -> owner != null ? DomainUtil.createParticipant(delegate) : null);
        } else {
            ret = Future.succeededFuture(DomainUtil.createParticipant(identity));
        }
        return ret;
    }

    /**
     * Applies the owning application's tenant policy to a new APPLICATION-scope user: when the
     * platform manages the app's tenants and no explicit tenantId was supplied, a fresh UUID
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
                    if (app.getTenantManagement() == TenantManagement.PLATFORM) {
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
    public Future<DelegatingParticipantIdentity> findOrCreateDelegate(Participant owner,
                                                                      DelegateKind kind,
                                                                      String clientKey,
                                                                      String displayName) {
        Validate.notNull(owner, "owner is required");
        Validate.notBlank(owner.getId(), "owner id is required");
        Validate.notNull(kind, "kind is required");
        Validate.notBlank(clientKey, "clientKey is required");
        Validate.notBlank(displayName, "displayName is required");
        ParticipantScope scope = DomainUtil.scopeOf(owner);

        return identityRepository.findByOwnerAndClientKey(owner.getId(), clientKey, scope.organizationId(), scope.applicationId())
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
                                // the delegate wields the owner's authority, so it lives at the exact
                                // scope the owner is signed in to, tenant included
                                .setOrganizationId(scope.organizationId())
                                .setApplicationId(scope.applicationId())
                                .setTenantId(scope.tenantId())
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
    public Future<Page<DelegatingParticipantIdentity>> findDelegatesByOwner(Participant owner, Pageable pageable) {
        ParticipantScope scope = DomainUtil.scopeOf(owner);
        return identityRepository.findDelegatesByOwner(owner.getId(), scope.organizationId(), scope.applicationId(), pageable);
    }

    @Override
    public Future<MachineProvisionResult> createMachine(MachineParticipantIdentity machine) {
        Validate.notNull(machine, "machine is required");
        Validate.notNull(machine.getMachineKind(), "machine.machineKind is required");

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
        // the memberships and the bindings go with the account as well: neither means anything without it
        return credentialRepository.deleteById(id)
                .compose(v -> bindingRepository.findByIdentity(id))
                .compose(bindings -> Future.all(bindings.stream().map(binding -> bindingRepository.deleteById(binding.getId())).toList()))
                .compose(v -> membershipRepository.findByIdentity(id))
                .compose(memberships -> Future.all(memberships.stream().map(membership -> membershipRepository.deleteById(membership.getId())).toList()))
                .compose(v -> identityRepository.findAllDelegatesByOwner(id, Pageable.create(0, 1000, Sort.by("created"))))
                .compose(delegates -> {
                    List<Future<Void>> deletes = delegates.getContent().stream()
                            .map(delegate -> deleteById(delegate.getId()))
                            .toList();
                    return Future.all(deletes).mapEmpty();
                });
    }

}
