package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.security.ConnectionRevocationService;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.DelegateType;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.identity.ScopedParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.exceptions.UserDisabledException;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.domain.internal.api.model.IdentityCredential;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.IdentityCredentialRepository;
import org.kinotic.domain.internal.api.repositories.DeviceCodeGrantRepository;
import org.kinotic.domain.internal.api.repositories.OAuthAuthorizationGrantRepository;
import org.kinotic.domain.internal.api.repositories.OidcBindingRepository;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class DefaultParticipantIdentityService extends AbstractCrudService<ParticipantIdentity> implements ParticipantIdentityService {

    /** Bytes of entropy for a machine client secret — generated, never user-chosen. */
    private static final int MACHINE_SECRET_BYTES = 32;

    private final ParticipantIdentityRepository identityRepository;
    private final IdentityCredentialRepository credentialRepository;
    private final OidcBindingRepository bindingRepository;
    private final DeviceCodeGrantRepository deviceCodeGrantRepository;
    private final OAuthAuthorizationGrantRepository oauthAuthorizationGrantRepository;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationRepository applicationRepository;
    private final ConnectionRevocationService connectionRevocationService;

    public DefaultParticipantIdentityService(ParticipantIdentityRepository repository,
                                             IdentityCredentialRepository credentialRepository,
                                             OidcBindingRepository bindingRepository,
                                             DeviceCodeGrantRepository deviceCodeGrantRepository,
                                             OAuthAuthorizationGrantRepository oauthAuthorizationGrantRepository,
                                             RefreshTokenService refreshTokenService,
                                             ApplicationRepository applicationRepository,
                                             ConnectionRevocationService connectionRevocationService) {
        super(repository);
        this.identityRepository = repository;
        this.credentialRepository = credentialRepository;
        this.bindingRepository = bindingRepository;
        this.deviceCodeGrantRepository = deviceCodeGrantRepository;
        this.oauthAuthorizationGrantRepository = oauthAuthorizationGrantRepository;
        this.refreshTokenService = refreshTokenService;
        this.applicationRepository = applicationRepository;
        this.connectionRevocationService = connectionRevocationService;
    }

    // A connection is authenticated once, when it is established, so an identity that is disabled or deleted keeps
    // the connections it holds until they are ended here. The revocation follows the write, so a reconnect finds
    // the stored state and is refused.
    @Override
    public Future<ParticipantIdentity> save(ParticipantIdentity value) {
        return super.save(value).compose(this::revokeConnectionsIfDisabled);
    }

    @Override
    public Future<ParticipantIdentity> saveSync(ParticipantIdentity value) {
        return super.saveSync(value).compose(this::revokeConnectionsIfDisabled);
    }

    @Override
    public Future<Void> deleteById(String id) {
        return super.deleteById(id).compose(v -> connectionRevocationService.revokeIdentity(id));
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return super.deleteByIdSync(id).compose(v -> connectionRevocationService.revokeIdentity(id));
    }

    // A delegate's connection is registered under the delegate's own id and acts with its owner's authority, so a
    // disabled owner's delegates lose their connections with it
    private Future<ParticipantIdentity> revokeConnectionsIfDisabled(ParticipantIdentity saved) {
        Future<ParticipantIdentity> ret;
        if (saved.isEnabled()) {
            ret = Future.succeededFuture(saved);
        } else {
            ret = connectionRevocationService.revokeIdentity(saved.getId())
                    .compose(v -> identityRepository.findAllDelegatesByOwner(saved.getId(), Pageable.create(0, 1000, Sort.by("created"))))
                    .compose(delegates -> Future.all(delegates.getContent().stream()
                                                              .map(delegate -> connectionRevocationService.revokeIdentity(delegate.getId()))
                                                              .toList()))
                    .map(saved);
        }
        return ret;
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
                Validate.notNull(delegate.getDelegateType(), "DELEGATE delegateType is required");
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
                // the machine's id is its client_id, so uniqueness is the id's own
                yield Future.succeededFuture();
            }
        };
    }

    /**
     * A {@link ScopedParticipantIdentity}'s scope must be one of the three valid shapes:
     * <ul>
     *   <li>SYSTEM: {@code organizationId}, {@code applicationId} and {@code tenantId} null</li>
     *   <li>ORGANIZATION: {@code organizationId} set, {@code applicationId} and {@code tenantId} null</li>
     *   <li>APPLICATION: {@code organizationId} and {@code applicationId} set; {@code tenantId} optional</li>
     * </ul>
     * an organization's user's scope must be ORGANIZATION and an application's user's APPLICATION.
     */
    private void validateScopeFields(ParticipantIdentity entity) {
        if (entity instanceof ScopedParticipantIdentity scoped) {
            ParticipantScope scope = scoped.scope();
            if (scope.applicationId() != null) {
                Validate.notBlank(scope.organizationId(),
                                  "ParticipantIdentity organizationId is required when applicationId is set");
            } else if (scope.tenantId() != null) {
                throw new IllegalArgumentException(
                        "ParticipantIdentity tenantId must be null when applicationId is null (tenantId is "
                        + "meaningful only for APPLICATION-scope identities)");
            }
        }
        if (entity instanceof OrganizationParticipantIdentity user) {
            Validate.notBlank(user.getOrganizationId(), "An ORGANIZATION_USER requires its organizationId");
        } else if (entity instanceof ApplicationParticipantIdentity user) {
            Validate.notBlank(user.getApplicationId(), "An APPLICATION_USER requires its applicationId");
        }
    }

    /**
     * Service-layer guarantee: at most one system user per email, one user of each organization and one of each
     * application. Self-id is excluded so updating an existing user doesn't trip on its own row.
     */
    private Future<Void> enforceUniqueEmailInScope(UserParticipantIdentity entity) {
        return findSameKindByEmail(entity)
                .compose(existing -> {
                    if (existing != null && !existing.getId().equals(entity.getId())) {
                        throw emailTaken(entity);
                    }
                    return Future.succeededFuture();
                });
    }

    // the user of the same kind and scope as user that has its email, which uniqueness is held to
    private Future<UserParticipantIdentity> findSameKindByEmail(UserParticipantIdentity user) {
        return switch (user) {
            case SystemParticipantIdentity system ->
                    identityRepository.findSystemUserByEmail(system.getEmail()).map(UserParticipantIdentity.class::cast);
            case OrganizationParticipantIdentity member ->
                    identityRepository.findOrganizationUserByEmail(member.getEmail(), member.getOrganizationId()).map(UserParticipantIdentity.class::cast);
            case ApplicationParticipantIdentity application ->
                    identityRepository.findApplicationUserByEmail(application.getEmail(), application.getOrganizationId(),
                                                                  application.getApplicationId()).map(UserParticipantIdentity.class::cast);
        };
    }

    private static ExistingUserException emailTaken(UserParticipantIdentity user) {
        String message = switch (user) {
            case SystemParticipantIdentity system -> "A user with email " + system.getEmail() + " already exists.";
            case OrganizationParticipantIdentity member -> "A member with email " + member.getEmail() + " already exists in this organization.";
            case ApplicationParticipantIdentity application -> "A user of application " + application.getApplicationId() + " with email "
                    + application.getEmail() + " already exists.";
        };
        return new ExistingUserException(message);
    }

    // a name-based UUID of the kind of user, its scope and its email, so the users of one email of one kind in one
    // scope contend for one id
    private static String userId(UserParticipantIdentity user) {
        ParticipantScope scope = DomainUtil.scopeOf(user);
        String key = user.getType() + "/" + Objects.toString(scope.organizationId(), "") + "/"
                + Objects.toString(scope.applicationId(), "") + "/" + DomainUtil.normalizeEmail(user.getEmail());
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Service-layer guarantee: at most one {@link DelegatingParticipantIdentity} per
     * {@code (ownerId, clientKey)} within one scope, since a user approves the same client once per
     * scope it acts in. Self-id is excluded so updating an existing delegate doesn't trip on its own row.
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
    public Future<SystemParticipantIdentity> findSystemUserByEmail(String email) {
        return identityRepository.findSystemUserByEmail(email);
    }

    @Override
    public Future<OrganizationParticipantIdentity> findOrganizationUserByEmail(String email, String organizationId) {
        return identityRepository.findOrganizationUserByEmail(email, organizationId);
    }

    @Override
    public Future<ApplicationParticipantIdentity> findApplicationUserByEmail(String email, String organizationId, String applicationId) {
        return identityRepository.findApplicationUserByEmail(email, organizationId, applicationId);
    }

    @Override
    public Future<UserParticipantIdentity> findUserByEmail(String email, String organizationId, String applicationId) {
        return applicationId != null
                ? findApplicationUserByEmail(email, organizationId, applicationId).map(UserParticipantIdentity.class::cast)
                : findOrganizationUserByEmail(email, organizationId).map(UserParticipantIdentity.class::cast);
    }

    @Override
    public Future<OrganizationParticipantIdentity> findOrganizationUserByOidcIdentity(String oidcConfigId, String subject, String organizationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return findByOidcIdentity(oidcConfigId, subject, organizationId, null)
                .map(identity -> identity instanceof OrganizationParticipantIdentity user ? user : null);
    }

    @Override
    public Future<List<OrganizationParticipantIdentity>> findOrganizationUsersByOidcIdentity(String oidcConfigId, String subject) {
        return bindingRepository.findOrganizationBindings(oidcConfigId, subject)
                                .compose(bindings -> identityRepository.findAllById(bindings.stream().map(OidcBinding::getIdentityId).toList()))
                                .map(identities -> identities.stream()
                                                             .filter(OrganizationParticipantIdentity.class::isInstance)
                                                             .map(OrganizationParticipantIdentity.class::cast)
                                                             .toList());
    }

    @Override
    public Future<ApplicationParticipantIdentity> findApplicationUserByOidcIdentity(String oidcConfigId,
                                                                                        String subject,
                                                                                        String organizationId,
                                                                                        String applicationId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return findByOidcIdentity(oidcConfigId, subject, organizationId, applicationId)
                .map(identity -> identity instanceof ApplicationParticipantIdentity user ? user : null);
    }

    // the identity the binding of (oidcConfigId, subject) in the scope signs in, or null
    private Future<ParticipantIdentity> findByOidcIdentity(String oidcConfigId, String subject, String organizationId, String applicationId) {
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(subject, "subject cannot be blank");
        return bindingRepository.findById(OidcBinding.idOf(oidcConfigId, subject, organizationId, applicationId))
                                .compose(binding -> binding == null
                                        ? Future.succeededFuture()
                                        : identityRepository.findById(binding.getIdentityId()));
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

    // the binding's id carries the user's scope, so the same provider identity binds to one user of each organization
    // and of each application
    private Future<OidcBinding> bind(UserParticipantIdentity user, String oidcConfigId, String subject) {
        requireBindable(user);
        ParticipantScope scope = DomainUtil.scopeOf(user);
        String bindingId = OidcBinding.idOf(oidcConfigId, subject, scope.organizationId(), scope.applicationId());
        return bindingRepository.findById(bindingId).compose(existing -> {
            Future<OidcBinding> ret;
            if (existing != null) {
                Validate.isTrue(user.getId().equals(existing.getIdentityId()), "This identity already signs in another user");
                ret = Future.succeededFuture(existing);
            } else {
                // sync so the profile's list of sign-in methods shows the binding at once
                ret = bindingRepository.saveSync(new OidcBinding().setId(bindingId)
                                                                  .setIdentityId(user.getId())
                                                                  .setOrganizationId(scope.organizationId())
                                                                  .setApplicationId(scope.applicationId())
                                                                  .setOidcConfigId(oidcConfigId)
                                                                  .setSubject(subject)
                                                                  .setCreated(new Date()))
                                        .compose(binding -> signsIn(user).map(binding));
            }
            return ret;
        });
    }

    // a user with a sign-in method is pending no longer; the flag alone is written, so an administrator's change to
    // the user meanwhile stands, and sync so the member list shows it at once
    private Future<Void> signsIn(UserParticipantIdentity user) {
        return user.isPending() ? identityRepository.clearPending(user.getId(), null).mapEmpty() : Future.succeededFuture();
    }

    // a system user signs in to the system console with a password alone
    private static void requireBindable(UserParticipantIdentity user) {
        Validate.isTrue(!(user instanceof SystemParticipantIdentity), "A system user signs in with a password alone");
    }

    @Override
    public Future<List<OidcBinding>> findOidcBindings(String identityId) {
        return bindingRepository.findByIdentity(identityId);
    }

    @Override
    public Future<Participant> claimProvisionedOrganizationUser(String oidcConfigId, String subject, String verifiedEmail, String organizationId) {
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(subject, "subject cannot be blank");
        Validate.notBlank(verifiedEmail, "verifiedEmail cannot be blank");
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return findOrganizationUserByEmail(verifiedEmail, organizationId)
                .compose(user -> claim(user, oidcConfigId, subject))
                .map(claimed -> claimed != null ? DomainUtil.createParticipant(claimed) : null);
    }

    @Override
    public Future<ApplicationParticipantIdentity> claimProvisionedApplicationUser(String oidcConfigId, String subject, String verifiedEmail, LoginScope scope) {
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(subject, "subject cannot be blank");
        Validate.notBlank(verifiedEmail, "verifiedEmail cannot be blank");
        Validate.notNull(scope, "scope cannot be null");
        return findApplicationUserByEmail(verifiedEmail, scope.applicationKey().organizationId(), scope.applicationKey().applicationId())
                .compose(user -> claim(scope.memberOf(user), oidcConfigId, subject))
                .map(ApplicationParticipantIdentity.class::cast);
    }

    // user bound to the identity as its first sign-in method, or null when there is no user or someone signs in as it
    private Future<UserParticipantIdentity> claim(UserParticipantIdentity found, String oidcConfigId, String subject) {
        // found is a search hit, which may predate an administrator's latest change, and the claim saves the user
        Future<UserParticipantIdentity> current = found != null
                ? identityRepository.findById(found.getId()).map(UserParticipantIdentity.class::cast)
                : Future.succeededFuture();
        return current.compose(user -> {
            Future<UserParticipantIdentity> ret;
            if (user == null || !user.isPending()) {
                ret = Future.succeededFuture();
            } else if (!user.isEnabled()) {
                ret = Future.failedFuture(new UserDisabledException("This user cannot sign in."));
            } else {
                // an administrator created the user and nobody else can sign in as it, so the scope's own provider's
                // word on the email is what claims it
                ret = bind(user, oidcConfigId, subject).map(user);
            }
            return ret;
        });
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
    public Future<Page<SystemParticipantIdentity>> findSystemUsers(Pageable pageable) {
        return identityRepository.findSystemUsers(pageable);
    }

    @Override
    public Future<Page<SystemParticipantIdentity>> searchSystemUsers(String searchText, Pageable pageable) {
        return identityRepository.searchSystemUsers(searchText, pageable);
    }

    @Override
    public Future<Page<OrganizationParticipantIdentity>> findOrganizationUsers(String organizationId, Pageable pageable) {
        return identityRepository.findOrganizationUsers(organizationId, pageable);
    }

    @Override
    public Future<Page<OrganizationParticipantIdentity>> searchOrganizationUsers(String searchText, String organizationId, Pageable pageable) {
        return identityRepository.searchOrganizationUsers(searchText, organizationId, pageable);
    }

    @Override
    public Future<Page<ApplicationParticipantIdentity>> findApplicationUsers(String organizationId, String applicationId, Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return identityRepository.findApplicationUsers(organizationId, applicationId, pageable);
    }

    @Override
    public Future<Page<ApplicationParticipantIdentity>> searchApplicationUsers(String searchText,
                                                                                  String organizationId,
                                                                                  String applicationId,
                                                                                  Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return identityRepository.searchApplicationUsers(searchText, organizationId, applicationId, pageable);
    }

    @Override
    public <T extends UserParticipantIdentity> Future<T> createUser(T user, String password) {
        Validate.notNull(user.getEmail(), "UserParticipantIdentity email cannot be null");
        validateScopeFields(user);

        if (user.getId() == null) {
            user.setId(userId(user));
        }

        Date now = new Date();
        user.setCreated(now);
        user.setUpdated(now);
        user.setEnabled(true);
        if (password != null) {
            user.setPending(false);
        }

        return applyTenantPolicy(user)
                // sync: the caller's next step finds the user by email, a search that misses a user written within the
                // index's refresh window (MemberService.sendInvitation right after addOrganizationUser, an invitation's acceptance)
                .compose(this::createSync)
                // beforeSave's lookup is a search, which misses a user written within the index's refresh window; the
                // create refuses an id already held, which the index guarantees whatever its shards, so of two creations
                // under one email at once exactly one succeeds
                .recover(err -> err instanceof AlreadyExistsException ? Future.failedFuture(emailTaken(user)) : Future.failedFuture(err))
                .compose(saved -> password != null
                        ? saveCredential(user.getId(), password).map(user)
                        : Future.succeededFuture(user));
    }

    @Override
    public <T extends UserParticipantIdentity> Future<T> createOidcUser(T user, String oidcConfigId, String subject) {
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(subject, "subject cannot be blank");
        requireBindable(user);
        ParticipantScope scope = DomainUtil.scopeOf(user);
        // checked before the user exists, so a bound identity leaves no user behind that no method signs in
        return bindingRepository.findById(OidcBinding.idOf(oidcConfigId, subject, scope.organizationId(), scope.applicationId()))
                                .compose(existing -> {
                                    if (existing != null) {
                                        throw new ExistingUserException("This identity already signs in another user");
                                    }
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
        if (identity == null || !identity.isEnabled() || !actsIn(identity, organizationId, applicationId)) {
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

    // a system user acts in the SYSTEM scope, and a scoped identity in the scope it carries
    private static boolean actsIn(ParticipantIdentity identity, String organizationId, String applicationId) {
        ParticipantScope scope = DomainUtil.scopeOf(identity);
        return Objects.equals(scope.organizationId(), organizationId) && Objects.equals(scope.applicationId(), applicationId);
    }

    /**
     * Applies the owning application's tenant policy to a new APPLICATION-scope user: when the
     * platform manages the app's tenants and no explicit tenantId was supplied, a fresh UUID
     * becomes the user's tenantId. Deliberately NOT the user's id — the tenantId is an ES
     * routing key and part of the immutable _id of every SHARED entity the user writes, while
     * createUser accepts caller-supplied ids of any shape; a dedicated UUID keeps tenant
     * identity decoupled from id semantics.
     */
    private <T extends UserParticipantIdentity> Future<T> applyTenantPolicy(T user) {
        if (!(user instanceof ApplicationParticipantIdentity applicationUser) || applicationUser.getTenantId() != null) {
            return Future.succeededFuture(user);
        }
        return applicationRepository.findById(applicationUser.getApplicationId(), applicationUser.getOrganizationId())
                .map(app -> {
                    if (app == null) {
                        throw new IllegalArgumentException(
                                "Application " + applicationUser.getApplicationId() + " not found in organization "
                                + applicationUser.getOrganizationId());
                    }
                    if (app.getTenantManagement() == TenantManagement.SYSTEM) {
                        applicationUser.setTenantId(UUID.randomUUID().toString());
                    } else {
                        throw new IllegalArgumentException("A user of application " + app.getId() + " belongs to one of its tenants,"
                                                           + " which its administrators manage, so the tenant must be named.");
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
                                                                      DelegateType kind,
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
                                .setDelegateType(kind)
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
        Validate.notNull(machine.getMachineType(), "machine.machineType is required");

        Date now = new Date();
        machine.setCreated(now);
        machine.setEnabled(true);

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
        // Everything that signs the identity in or that it authorized goes with it: its credential, read by id
        // so its delete never waits for search visibility; its bindings; the device-code and authorization-code
        // grants it approved, which would otherwise redeem to a deleted identity until they expire; and its
        // refresh tokens, revoked so a rotation of one fails. Delegates it owns go with it as well — a delegate
        // must not outlive the authority it wields (resolveParticipant would reject it anyway; this is hygiene).
        return credentialRepository.deleteById(id)
                .compose(v -> bindingRepository.deleteByIdentity(id))
                .compose(v -> deviceCodeGrantRepository.deleteByIdentity(id))
                .compose(v -> oauthAuthorizationGrantRepository.deleteByIdentity(id))
                .compose(v -> refreshTokenService.revokeAllFor(id))
                .compose(v -> identityRepository.findAllDelegatesByOwner(id, Pageable.create(0, 1000, Sort.by("created"))))
                .compose(delegates -> {
                    List<Future<Void>> deletes = delegates.getContent().stream()
                            .map(delegate -> deleteById(delegate.getId()))
                            .toList();
                    return Future.all(deletes).mapEmpty();
                });
    }

}
