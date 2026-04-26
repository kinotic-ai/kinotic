package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.os.api.model.iam.IamUser;

import java.util.concurrent.CompletableFuture;

@Publish
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

//    /**
//     * Finds all users registered against the given auth scope.
//     *
//     * @param authScopeType the scope type to filter by (e.g. {@code SYSTEM}, {@code ORGANIZATION},
//     *                      {@code APPLICATION})
//     * @param authScopeId the id of the scope to filter by
//     * @param pageable the paging and sort options
//     * @return {@link CompletableFuture} emitting a page of users registered against the scope
//     */
//    CompletableFuture<Page<IamUser>> findByScope(String authScopeType, String authScopeId, Pageable pageable);
//
//    /**
//     * Creates a user and, if a password is provided, the matching credential.
//     * {@code APPLICATION}-scoped users must carry a {@code tenantId}; {@code SYSTEM} and
//     * {@code ORGANIZATION} users must not.
//     *
//     * @param user the user to create
//     * @param password the password to set, or {@code null} to create the user without a credential
//     * @return {@link CompletableFuture} emitting the persisted user
//     */
//    CompletableFuture<IamUser> createUser(IamUser user, String password);
//
//    /**
//     * Verifies the current password and updates it. Used when the user knows their current password.
//     *
//     * @param userId the id of the user whose password should be changed
//     * @param currentPassword the user's current password
//     * @param newPassword the new password to set
//     * @return {@link CompletableFuture} that completes when the password has been updated
//     */
//    CompletableFuture<Void> changePassword(String userId, String currentPassword, String newPassword);
//
//    /**
//     * Replaces the user's password without verifying the current one. Administrative reset.
//     *
//     * @param userId the id of the user whose password should be reset
//     * @param newPassword the new password to set
//     * @return {@link CompletableFuture} that completes when the password has been reset
//     */
//    CompletableFuture<Void> resetPassword(String userId, String newPassword);

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

}

