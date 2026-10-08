package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.model.security.DelegateKind;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineKind;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineProvisionResult;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

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
     * Finds the first ORG-scope user with the given email across all organizations, for a flow
     * that runs before an organization is known: the sign-up flow enforcing one user per email
     * before the new organization's id exists, and organization login.
     */
    Future<UserParticipantIdentity> findFirstOrgUserByEmail(String email);

    /**
     * Finds the {@link UserParticipantIdentity} (if any) with the given OIDC identity within a specific
     * scope. Scope is identified by {@code (organizationId, applicationId)} with the same
     * null conventions as {@link #findByEmail(String, String, String)}.
     */
    Future<UserParticipantIdentity> findByOidcIdentity(String oidcSubject,
                                                       String oidcConfigId,
                                                       String organizationId,
                                                       String applicationId);

    /**
     * Finds the ORGANIZATION-scope user (if any) for the given OIDC identity, across all
     * organizations. Org-tier social login and sign-up resolve identities with this lookup;
     * it is unambiguous because an email may belong to at most one organization, so a given
     * {@code (oidcSubject, oidcConfigId)} identity maps to at most one org-scope user.
     */
    Future<UserParticipantIdentity> findOrgUserByOidcIdentity(String oidcSubject, String oidcConfigId);

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
     * Finds the users (never delegates) of one tenant of an application.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application
     * @param tenantId       the tenant
     */
    Future<Page<UserParticipantIdentity>> findUsersByTenant(String organizationId,
                                                            String applicationId,
                                                            String tenantId,
                                                            Pageable pageable);

    /**
     * Creates a user, assigning id and dates, enabling it, and detecting the auth type from
     * password presence (LOCAL when given, OIDC otherwise — a LOCAL credential is created
     * alongside). Enforces one user per email within the scope. For APPLICATION-scope users
     * whose application isolates each user ({@code OnboardingMechanism.TENANT_PER_USER}), a tenant of
     * their own is created, under the tenantId supplied or a fresh one, its id set, and the user bound as its
     * administrator, holding every permission in it; such an application holds one user per tenant, so a tenant
     * that exists already fails the creation.
     *
     * @param user     the unsaved user carrying email, scope, and optional display name / OIDC identity
     * @param password the password for a LOCAL user, or null for an OIDC user
     * @return a future emitting the created user
     */
    Future<UserParticipantIdentity> createUser(UserParticipantIdentity user, String password);

    /**
     * The user an identity provider signed in to an application, by the identity it asserted: the user holding
     * it, or, for a configuration a tenant owns, a user created in that tenant on this first sign-in with the
     * email and name the provider asserted, granted the role the tenant gives its provider's users. A
     * configuration no tenant owns creates nobody. Fails with {@link AlreadyExistsException} when the email
     * already belongs to a user of the application, so a provider never takes over an account made another way
     * or in another tenant.
     *
     * @param configuration the configuration the provider was reached through
     * @param oidcSubject   the {@code sub} claim
     * @param email         the verified email claim, required to create a user
     * @param displayName   the name claim, or null
     * @return the user, or null when none holds the identity and none is created
     */
    Future<UserParticipantIdentity> findOrCreateSsoUser(OidcConfiguration configuration,
                                                        String oidcSubject,
                                                        String email,
                                                        String displayName);

    /**
     * Resolves the delegate for {@code (owner, clientKey)}, creating it on first approval.
     * The delegate carries the owner's scope and authenticates with DELEGATED tokens only.
     * A re-approval refreshes {@code displayName} from the latest client metadata and
     * re-enables a previously revoked delegate — the owner just consented again.
     *
     * @param owner       the user whose approval authorizes the client
     * @param kind        the kind of client being authorized
     * @param clientKey   stable client identity, unique per owner
     * @param displayName the client's display name, shown wherever the delegate is listed
     * @return a future emitting the enabled delegate
     */
    Future<DelegatingParticipantIdentity> findOrCreateDelegate(UserParticipantIdentity owner,
                                                               DelegateKind kind,
                                                               String clientKey,
                                                               String displayName);

    /**
     * Finds every delegate authorized on behalf of the given owner, revoked ones included —
     * the owner's view of which clients hold or held access.
     */
    Future<Page<DelegatingParticipantIdentity>> findDelegatesByOwner(String ownerId, Pageable pageable);

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


