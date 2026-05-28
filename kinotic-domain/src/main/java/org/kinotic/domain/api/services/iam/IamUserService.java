package org.kinotic.domain.api.services.iam;

import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.iam.IamUser;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IamUserService extends IdentifiableCrudService<IamUser, String> {

    /**
     * Finds the user with the given email within the given auth scope.
     *
     * @param email the email address to look up
     * @param authScopeType the scope type the user is registered against (e.g. {@code SYSTEM},
     *                      {@code ORGANIZATION}, {@code APPLICATION})
     * @param authScopeId the id of the scope the user is registered against
     * @return {@link CompletableFuture} emitting the matching user, or {@code null} if no user matches
     */
    CompletableFuture<IamUser> findByEmailAndScope(String email, String authScopeType, String authScopeId);

    /**
     * Finds the first user with the given email across all scope ids of the given scope type.
     * Used by the sign-up flow to enforce one user per email at organization-creation time,
     * before the new organization's scope id exists.
     *
     * @param email the email address to look up
     * @param authScopeType the scope type to search within (e.g. {@code ORGANIZATION})
     * @return {@link CompletableFuture} emitting the first matching user, or {@code null} if no user matches
     */
    CompletableFuture<IamUser> findFirstByEmailInScopeType(String email, String authScopeType);

    /**
     * Finds the {@link IamUser} record for the given email. Returns {@code null} if no
     * user matches. Used by the email-first login lookup to decide between password vs
     * SSO redirect — the service-layer uniqueness rule (one row per email + scope) makes
     * this an unambiguous lookup for the org-login flow.
     */
    CompletableFuture<IamUser> findByEmail(String email);

    /**
     * Finds the {@link IamUser} (if any) with the given OIDC identity within a specific scope.
     * The composite {@code (oidcSubject, oidcConfigId)} uniquely identifies a person-at-an-IdP;
     * adding the scope narrows to a single org/application record.
     */
    CompletableFuture<IamUser> findByOidcIdentityAndScope(String oidcSubject,
                                                         String oidcConfigId,
                                                         String authScopeType,
                                                         String authScopeId);

    /**
     * Finds all {@link IamUser} records across scopes for a given OIDC identity. Used by the
     * post-login org switcher to enumerate the orgs this identity can access.
     */
    CompletableFuture<List<IamUser>> findByOidcIdentity(String oidcSubject, String oidcConfigId);


    CompletableFuture<IamUser> createUser(IamUser user, String password);

    /**
     * Builds the {@link Participant} security identity for an authenticated {@link IamUser}.
     * The returned subtype matches {@code user.getAuthScopeType()}:
     * <ul>
     *   <li>{@code SYSTEM} → {@code SystemParticipant} (completed synchronously)</li>
     *   <li>{@code ORGANIZATION} → {@code OrganizationParticipant} with
     *       {@code organizationId = user.getAuthScopeId()} (completed synchronously)</li>
     *   <li>{@code APPLICATION} → {@code ApplicationParticipant}; the organization is sourced
     *       by looking up the Application referenced by {@code user.getAuthScopeId()}</li>
     * </ul>
     *
     * @param user the authenticated user
     * @return future emitting the typed participant; fails if {@code user} is APP-scoped and
     *         no Application matches its {@code authScopeId}
     */
    CompletableFuture<Participant> createParticipant(IamUser user);

}
