package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.DelegateKind;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineKind;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

import java.util.List;

public interface ParticipantIdentityService extends IdentifiableCrudService<ParticipantIdentity, String> {

    /**
     * Finds the user with the given email within the given scope, identified structurally
     * by {@code (organizationId, applicationId)}:
     * <ul>
     *   <li>both null → SYSTEM</li>
     *   <li>{@code organizationId} only → ORGANIZATION (in that org)</li>
     *   <li>both set → APPLICATION (in that app within that org)</li>
     * </ul>
     * Passing only {@code applicationId} (no {@code organizationId}) is an error and is
     * rejected at the service layer.
     *
     * @param email the email address to look up
     * @param organizationId the owning org id, or null for SYSTEM
     * @param applicationId the owning app id, or null for SYSTEM/ORGANIZATION
     * @return {@link Future} emitting the matching user, or {@code null} if no user matches
     */
    Future<UserParticipantIdentity> findByEmail(String email, String organizationId, String applicationId);

    /**
     * Finds the user of the scope {@code (organizationId, applicationId)} the OIDC identity
     * {@code (oidcConfigId, subject)} is bound to, with the same null conventions as
     * {@link #findByEmail(String, String, String)}: both null finds an account. A provider identity binds to
     * one account and to one user of each application, so {@code null} says it is bound to no user of that
     * scope.
     */
    Future<UserParticipantIdentity> findByOidcIdentity(String oidcConfigId,
                                                       String subject,
                                                       String organizationId,
                                                       String applicationId);

    /**
     * Binds the OIDC identity {@code (oidcConfigId, subject)} to user {@code identityId} as one of its sign-in
     * methods, so a sign-in asserting that identity signs the user in. Binding an identity already bound to
     * the same user changes nothing.
     *
     * @return the binding, readable at once; fails with {@link IllegalArgumentException} when the identity is
     *         bound to another user of the same scope, or {@code identityId} is not a user
     */
    Future<OidcBinding> bindOidcIdentity(String identityId, String oidcConfigId, String subject);

    /** The OIDC identities bound to user {@code identityId}, oldest first. */
    Future<List<OidcBinding>> findOidcBindings(String identityId);

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

    /**
     * Finds all users (never delegates) within the given scope, identified structurally by
     * {@code (organizationId, applicationId)} with the same null conventions as
     * {@link #findByEmail(String, String, String)}.
     */
    Future<Page<UserParticipantIdentity>> findUsersByScope(String organizationId, String applicationId, Pageable pageable);

    /**
     * Searches users (never delegates) within the given scope by free text over email and
     * display name. A blank {@code searchText} returns every user in scope, equivalent to
     * {@link #findUsersByScope(String, String, Pageable)}.
     */
    Future<Page<UserParticipantIdentity>> searchUsersByScope(String searchText,
                                                             String organizationId,
                                                             String applicationId,
                                                             Pageable pageable);

    /**
     * Creates a user, assigning id and dates and enabling it: an account when the user carries no scope,
     * an application's user when it carries one. A password given becomes the user's credential;
     * {@code null} creates a user that signs in by an OIDC binding, added with
     * {@link #bindOidcIdentity} or by {@link #createOidcUser}. Enforces one user per email within the
     * scope. For APPLICATION-scope users whose application's tenants are {@code TenantManagement.PLATFORM}
     * managed, a unique {@code tenantId} is auto-generated unless one was supplied.
     *
     * @param user     the unsaved user carrying email, scope, and optional display name
     * @param password the password for a user that signs in with one, or null
     * @return a future emitting the created user
     */
    Future<UserParticipantIdentity> createUser(UserParticipantIdentity user, String password);

    /**
     * {@link #createUser(UserParticipantIdentity, String)} for a user that signs in by the OIDC identity
     * {@code (oidcConfigId, subject)}, which the new user is bound to. Fails with
     * {@link IllegalArgumentException} when the identity is bound to a user of the new user's scope already.
     */
    Future<UserParticipantIdentity> createOidcUser(UserParticipantIdentity user, String oidcConfigId, String subject);

    /**
     * The participant identity {@code identityId} acts as in the scope {@code (organizationId, applicationId)},
     * or {@code null} when it may not: an account acts in a scope through its enabled membership of it; a
     * scoped identity acts in its own scope only; a delegate acts while its owner may act in the delegate's
     * scope. A disabled identity acts nowhere.
     *
     * @param identityId     the identity, as a session or token names it
     * @param organizationId the organization of the scope, or null for the platform
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
     * first approval. The delegate carries that scope — the membership an account was signed in to when it
     * approved — and authenticates with DELEGATED tokens only. A re-approval refreshes {@code displayName}
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
                                                               DelegateKind kind,
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
     * @param machine the unsaved machine carrying display name, scope and {@link MachineKind}
     * @return a future emitting the saved machine together with its one-time secret
     */
    Future<MachineProvisionResult> createMachine(MachineParticipantIdentity machine);

    /**
     * Finds all machines within the given scope, identified structurally by
     * {@code (organizationId, applicationId)} with the same null conventions as
     * {@link #findByEmail(String, String, String)}.
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


