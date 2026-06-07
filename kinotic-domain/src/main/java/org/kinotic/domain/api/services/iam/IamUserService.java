package org.kinotic.domain.api.services.iam;

import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.IamUser;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IamUserService extends IdentifiableCrudService<IamUser, String> {

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
    CompletableFuture<IamUser> findByEmail(String email, String organizationId, String applicationId);

    /**
     * Finds the first ORG-scope user with the given email across all organizations. Used by
     * the sign-up flow to enforce one user per email at organization-creation time, before
     * the new organization's id exists.
     */
    CompletableFuture<IamUser> findFirstOrgUserByEmail(String email);

    /**
     * Finds the {@link IamUser} record for the given email, across all scopes. Returns
     * the first match. Used by the email-first login lookup to decide between password vs
     * SSO redirect — the service-layer uniqueness rule (one row per email + scope) makes
     * this an unambiguous lookup for the org-login flow.
     */
    CompletableFuture<IamUser> findByEmail(String email);

    /**
     * Finds the {@link IamUser} (if any) with the given OIDC identity within a specific
     * scope. Scope is identified by {@code (organizationId, applicationId)} with the same
     * null conventions as {@link #findByEmail(String, String, String)}.
     */
    CompletableFuture<IamUser> findByOidcIdentity(String oidcSubject,
                                                  String oidcConfigId,
                                                  String organizationId,
                                                  String applicationId);

    /**
     * Finds all {@link IamUser} records across scopes for a given OIDC identity. Used by the
     * post-login org switcher to enumerate the orgs this identity can access.
     */
    CompletableFuture<List<IamUser>> findAllByOidcIdentity(String oidcSubject, String oidcConfigId);

    /**
     * Finds all users within the given scope, identified structurally by
     * {@code (organizationId, applicationId)} with the same null conventions as
     * {@link #findByEmail(String, String, String)}.
     */
    CompletableFuture<Page<IamUser>> findByScope(String organizationId, String applicationId, Pageable pageable);

    /**
     * Searches users within the given scope by free text over email and display name. A blank
     * {@code searchText} returns every user in scope, equivalent to
     * {@link #findByScope(String, String, Pageable)}.
     */
    CompletableFuture<Page<IamUser>> searchByScope(String searchText,
                                                   String organizationId,
                                                   String applicationId,
                                                   Pageable pageable);

    CompletableFuture<IamUser> createUser(IamUser user, String password);

}


