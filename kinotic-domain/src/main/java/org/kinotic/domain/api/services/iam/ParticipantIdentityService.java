package org.kinotic.domain.api.services.iam;

import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.DelegateKind;
import org.kinotic.domain.api.model.iam.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.iam.ParticipantIdentity;
import org.kinotic.domain.api.model.iam.UserParticipantIdentity;

import java.util.concurrent.CompletableFuture;

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
     * @return {@link CompletableFuture} emitting the matching user, or {@code null} if no user matches
     */
    CompletableFuture<UserParticipantIdentity> findByEmail(String email, String organizationId, String applicationId);

    /**
     * Finds the first ORG-scope user with the given email across all organizations. Used by
     * the sign-up flow to enforce one user per email at organization-creation time, before
     * the new organization's id exists.
     */
    CompletableFuture<UserParticipantIdentity> findFirstOrgUserByEmail(String email);

    /**
     * Finds the {@link UserParticipantIdentity} record for the given email, across all scopes. Returns
     * the first match. Used by the email-first login lookup to decide between password vs
     * SSO redirect — the service-layer uniqueness rule (one row per email + scope) makes
     * this an unambiguous lookup for the org-login flow.
     */
    CompletableFuture<UserParticipantIdentity> findByEmail(String email);

    /**
     * Finds the {@link UserParticipantIdentity} (if any) with the given OIDC identity within a specific
     * scope. Scope is identified by {@code (organizationId, applicationId)} with the same
     * null conventions as {@link #findByEmail(String, String, String)}.
     */
    CompletableFuture<UserParticipantIdentity> findByOidcIdentity(String oidcSubject,
                                                  String oidcConfigId,
                                                  String organizationId,
                                                  String applicationId);

    /**
     * Finds the ORGANIZATION-scope user (if any) for the given OIDC identity, across all
     * organizations. Org-tier social login and sign-up resolve identities with this lookup;
     * it is unambiguous because an email may belong to at most one organization, so a given
     * {@code (oidcSubject, oidcConfigId)} identity maps to at most one org-scope user.
     */
    CompletableFuture<UserParticipantIdentity> findOrgUserByOidcIdentity(String oidcSubject, String oidcConfigId);

    /**
     * Finds all users (never delegates) within the given scope, identified structurally by
     * {@code (organizationId, applicationId)} with the same null conventions as
     * {@link #findByEmail(String, String, String)}.
     */
    CompletableFuture<Page<UserParticipantIdentity>> findUsersByScope(String organizationId, String applicationId, Pageable pageable);

    /**
     * Searches users (never delegates) within the given scope by free text over email and
     * display name. A blank {@code searchText} returns every user in scope, equivalent to
     * {@link #findUsersByScope(String, String, Pageable)}.
     */
    CompletableFuture<Page<UserParticipantIdentity>> searchUsersByScope(String searchText,
                                                   String organizationId,
                                                   String applicationId,
                                                   Pageable pageable);

    /**
     * Creates a user, assigning id and dates, enabling it, and detecting the auth type from
     * password presence (LOCAL when given, OIDC otherwise — a LOCAL credential is created
     * alongside). Enforces one user per email within the scope. For APPLICATION-scope users
     * whose application has {@code tenantPerUser} enabled, a unique {@code tenantId} is
     * auto-generated unless one was supplied.
     *
     * @param user     the unsaved user carrying email, scope, and optional display name / OIDC identity
     * @param password the password for a LOCAL user, or null for an OIDC user
     * @return a future emitting the created user
     */
    CompletableFuture<UserParticipantIdentity> createUser(UserParticipantIdentity user, String password);

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
    CompletableFuture<DelegatingParticipantIdentity> findOrCreateDelegate(UserParticipantIdentity owner,
                                                                          DelegateKind kind,
                                                                          String clientKey,
                                                                          String displayName);

    /**
     * Finds every delegate authorized on behalf of the given owner, revoked ones included —
     * the owner's view of which clients hold or held access.
     */
    CompletableFuture<Page<DelegatingParticipantIdentity>> findDelegatesByOwner(String ownerId, Pageable pageable);

}


