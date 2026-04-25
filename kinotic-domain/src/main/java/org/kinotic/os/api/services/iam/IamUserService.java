package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.os.api.model.iam.IamUser;

import java.util.concurrent.CompletableFuture;

@Publish
public interface IamUserService extends IdentifiableCrudService<IamUser, String> {

    CompletableFuture<IamUser> findByEmailAndScope(String email, String authScopeType, String authScopeId);

    /**
     * Finds the {@link IamUser} for the given email that is marked as that identity's default
     * org membership. Returns null if no user with this email exists or none is flagged as
     * default. Used by the email-first login lookup to decide between password vs SSO redirect.
     */
    CompletableFuture<IamUser> findByEmailDefault(String email);

    CompletableFuture<Page<IamUser>> findByScope(String authScopeType, String authScopeId, Pageable pageable);

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
    CompletableFuture<java.util.List<IamUser>> findByOidcIdentity(String oidcSubject, String oidcConfigId);

    CompletableFuture<IamUser> createUser(IamUser user, String password);

    CompletableFuture<Void> changePassword(String userId, String currentPassword, String newPassword);

    CompletableFuture<Void> resetPassword(String userId, String newPassword);

}
