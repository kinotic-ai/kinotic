package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.exceptions.UserDisabledException;
import org.kinotic.domain.api.model.security.DelegateType;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineType;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

import java.util.List;

public interface ParticipantIdentityService extends IdentifiableCrudService<ParticipantIdentity, String> {

    /** The system user with the email, or {@code null}. */
    Future<SystemParticipantIdentity> findSystemUserByEmail(String email);

    /** The user of organization {@code organizationId} with the email, or {@code null}. */
    Future<OrganizationParticipantIdentity> findOrganizationUserByEmail(String email, String organizationId);

    /** The user of application {@code applicationId} of organization {@code organizationId} with the email, or {@code null}. */
    Future<ApplicationParticipantIdentity> findApplicationUserByEmail(String email, String organizationId, String applicationId);

    /**
     * The user with the email of organization {@code organizationId}, or of its application {@code applicationId} when
     * that is set, or {@code null}.
     */
    Future<UserParticipantIdentity> findUserByEmail(String email, String organizationId, String applicationId);

    /**
     * The user of organization {@code organizationId} the OIDC identity {@code (oidcConfigId, subject)} is bound to,
     * or {@code null}. A provider identity binds to one user of each organization.
     */
    Future<OrganizationParticipantIdentity> findOrganizationUserByOidcIdentity(String oidcConfigId, String subject, String organizationId);

    /** The users of every organization the OIDC identity {@code (oidcConfigId, subject)} is bound to a user of, one of each. */
    Future<List<OrganizationParticipantIdentity>> findOrganizationUsersByOidcIdentity(String oidcConfigId, String subject);

    /**
     * The user of application {@code applicationId} of organization {@code organizationId} the OIDC identity
     * {@code (oidcConfigId, subject)} is bound to, or {@code null}. A provider identity binds to one user of each
     * application.
     */
    Future<ApplicationParticipantIdentity> findApplicationUserByOidcIdentity(String oidcConfigId,
                                                                                 String subject,
                                                                                 String organizationId,
                                                                                 String applicationId);

    /**
     * Binds the OIDC identity {@code (oidcConfigId, subject)} to user {@code identityId} as one of its sign-in
     * methods, so a sign-in asserting that identity signs the user in, and the user is no longer
     * {@link UserParticipantIdentity#isPending() pending}. Binding an identity already bound to the same user
     * changes nothing.
     *
     * @return the binding, readable at once; fails with {@link IllegalArgumentException} when the identity is
     *         bound to another user of the same scope, or {@code identityId} is not an organization's or an
     *         application's user
     */
    Future<OidcBinding> bindOidcIdentity(String identityId, String oidcConfigId, String subject);

    /** The OIDC identities bound to user {@code identityId}, oldest first. */
    Future<List<OidcBinding>> findOidcBindings(String identityId);

    /**
     * Signs the identity {@code (oidcConfigId, subject)}, verified by one of organization {@code organizationId}'s
     * own providers, in as the member the organization added under {@code verifiedEmail}: a
     * {@link UserParticipantIdentity#isPending() pending} user of the organization, which the identity is bound to as
     * its first sign-in method. {@code null} when the organization has no such member: no user of the organization
     * under the email, or one someone already signs in as, which only its holder may bind the identity to.
     *
     * @param oidcConfigId   the organization's own configuration that verified the identity
     * @param subject        the provider's {@code sub} claim for the identity
     * @param verifiedEmail  the email the provider verified for the identity
     * @param organizationId the organization whose login the identity signs in at
     * @return the member's participant, or {@code null}; fails with {@link UserDisabledException} when the member is
     *         disabled
     */
    Future<Participant> claimProvisionedOrganizationUser(String oidcConfigId, String subject, String verifiedEmail, String organizationId);

    /**
     * Signs the identity {@code (oidcConfigId, subject)}, verified by one of the application's providers, in as the
     * user an administrator added to {@code scope} under {@code verifiedEmail}: a
     * {@link UserParticipantIdentity#isPending() pending} user of the scope, which the identity is bound to as its
     * first sign-in method. {@code null} when the scope has no such
     * user: no user of the application under the email, one of another tenant than a tenant's login admits, or one
     * someone already signs in as, which only its holder may bind the identity to.
     *
     * @param oidcConfigId  the application's or the tenant's configuration that verified the identity
     * @param subject       the identifier the binding names the identity by
     * @param verifiedEmail the email the provider verified for the identity
     * @param scope         the application, or its tenant, whose login the identity signs in at
     * @return the user, or {@code null}; fails with {@link UserDisabledException} when the user is disabled
     */
    Future<ApplicationParticipantIdentity> claimProvisionedApplicationUser(String oidcConfigId, String subject, String verifiedEmail, LoginScope scope);

    /**
     * Removes binding {@code bindingId} from user {@code identityId}'s sign-in methods.
     *
     * @return fails with {@link IllegalArgumentException} when the binding is not the user's
     */
    Future<Void> removeOidcBinding(String identityId, String bindingId);

    /**
     * Whether user {@code identityId} holds a password, one of its sign-in methods beside its OIDC bindings.
     */
    Future<Boolean> hasPassword(String identityId);

    /**
     * Sets user {@code identityId}'s password, replacing the one it had or giving a user that signed in by
     * a provider alone a password as well.
     */
    Future<Void> setPassword(String identityId, String password);

    /** The system users. */
    Future<Page<SystemParticipantIdentity>> findSystemUsers(Pageable pageable);

    /** The system users whose email or display name matches {@code searchText}; blank text lists them all. */
    Future<Page<SystemParticipantIdentity>> searchSystemUsers(String searchText, Pageable pageable);

    /** The users of organization {@code organizationId}: its members. */
    Future<Page<OrganizationParticipantIdentity>> findOrganizationUsers(String organizationId, Pageable pageable);

    /**
     * The users of the organization whose email or display name matches {@code searchText}; blank text lists them
     * all, as {@link #findOrganizationUsers} does.
     */
    Future<Page<OrganizationParticipantIdentity>> searchOrganizationUsers(String searchText, String organizationId, Pageable pageable);

    /** The users of application {@code applicationId} of organization {@code organizationId}. */
    Future<Page<ApplicationParticipantIdentity>> findApplicationUsers(String organizationId, String applicationId, Pageable pageable);

    /**
     * The users of the application whose email or display name matches {@code searchText}; blank text lists them
     * all, as {@link #findApplicationUsers} does.
     */
    Future<Page<ApplicationParticipantIdentity>> searchApplicationUsers(String searchText,
                                                                           String organizationId,
                                                                           String applicationId,
                                                                           Pageable pageable);

    /**
     * Creates a user of the kind {@code user} is, assigning id and dates and enabling it. A password given becomes
     * the user's credential; {@code null} creates a user that signs in by an OIDC binding, added with
     * {@link #bindOidcIdentity} or by {@link #createOidcUser}, or, {@link UserParticipantIdentity#isPending() pending}
     * as {@code user} is marked, one an administrator added that its first sign-in method claims. One system user holds an email, one user of each
     * organization and one of each application: the id minted for the user is derived from its kind, its scope and
     * its email, and a creation under an email already held fails with
     * {@link org.kinotic.domain.api.exceptions.ExistingUserException}, two at once included.
     * Every APPLICATION-scope user belongs to a tenant: one whose application's tenants are
     * {@code TenantManagement.SYSTEM} managed gets a unique {@code tenantId} generated unless one was supplied, and
     * one of any other application must name its tenant or the creation fails with {@link IllegalArgumentException}.
     *
     * @param user     the unsaved user carrying email, scope, and optional display name
     * @param password the password for a user that signs in with one, or null
     * @return a future emitting the created user, which the lookup by email of its kind finds from then on
     */
    <T extends UserParticipantIdentity> Future<T> createUser(T user, String password);

    /**
     * {@link #createUser(UserParticipantIdentity, String)} for a user that signs in by the OIDC identity
     * {@code (oidcConfigId, subject)}, which the new user is bound to. Fails with
     * {@link IllegalArgumentException} when the identity is bound to a user of the new user's scope already, or for a
     * system user.
     */
    <T extends UserParticipantIdentity> Future<T> createOidcUser(T user, String oidcConfigId, String subject);

    /**
     * The participant identity {@code identityId} acts as in the scope {@code (organizationId, applicationId)},
     * or {@code null} when it may not: an identity acts in its own scope only, and a delegate while its owner may act
     * in the delegate's scope. A disabled identity acts nowhere.
     *
     * @param identityId     the identity, as a session or token names it
     * @param organizationId the organization of the scope, or null for the system
     * @param applicationId  the application of the scope, or null
     */
    Future<Participant> resolveParticipant(String identityId, String organizationId, String applicationId);

    /**
     * {@link #resolveParticipant(String, String, String)} for an identity the caller has already loaded, so
     * the authentication of every request reads it once.
     */
    Future<Participant> resolveParticipant(ParticipantIdentity identity, String organizationId, String applicationId);

    /**
     * Resolves the delegate for {@code (owner, clientKey)} in the scope {@code owner} acts in, creating it on
     * first approval. The delegate carries that scope and authenticates with DELEGATED tokens only. A re-approval refreshes {@code displayName}
     * from the latest client metadata and re-enables a previously revoked delegate — the owner just consented
     * again.
     *
     * @param owner       the signed-in user whose approval authorizes the client
     * @param kind        the kind of client being authorized
     * @param clientKey   stable client identity, unique per owner and scope
     * @param displayName the client's display name, shown wherever the delegate is listed
     * @return a future emitting the enabled delegate
     */
    Future<DelegatingParticipantIdentity> findOrCreateDelegate(Participant owner,
                                                               DelegateType kind,
                                                               String clientKey,
                                                               String displayName);

    /**
     * Finds every delegate the signed-in user {@code owner} authorized in the scope it acts in, revoked ones
     * included — the owner's view of which clients hold or held access there.
     */
    Future<Page<DelegatingParticipantIdentity>> findDelegatesByOwner(Participant owner, Pageable pageable);

    /**
     * Provisions a machine identity, assigning id and dates, enabling it, and generating the
     * client secret it connects with. The identity's id is its {@code clientId}; the secret is
     * returned in plaintext exactly once and stored only as a hash.
     *
     * @param machine the unsaved machine carrying display name, scope and {@link MachineType}
     * @return a future emitting the saved machine together with its one-time secret
     */
    Future<MachineProvisionResult> createMachine(MachineParticipantIdentity machine);

    /**
     * Finds all machines within the given scope, identified structurally by
     * {@code (organizationId, applicationId)}: both null for SYSTEM, {@code organizationId} alone for
     * ORGANIZATION, both set for APPLICATION.
     */
    Future<Page<MachineParticipantIdentity>> findMachinesByScope(String organizationId, String applicationId, Pageable pageable);

    /**
     * Replaces a machine's client secret with a freshly generated one, invalidating the old
     * secret immediately. Tokens the machine already holds run out on their own short TTL.
     *
     * @param machineId the machine whose secret to replace
     * @return a future emitting the new secret in plaintext, shown exactly once
     */
    Future<String> rotateMachineSecret(String machineId);

    /**
     * Authenticates a machine by its credentials: the machine with the given id must exist,
     * be enabled, and hold a credential matching {@code clientSecret}. Every failure — unknown
     * id, wrong kind of identity, disabled, or wrong secret — fails the same way, so callers
     * leak nothing about which check missed.
     *
     * @param machineId    the machine's id, presented as the {@code clientId} credential
     * @param clientSecret the secret issued at provisioning
     * @return a future emitting the authenticated machine, failed for any invalid credential
     */
    Future<MachineParticipantIdentity> verifyMachineCredentials(String machineId, String clientSecret);

}


