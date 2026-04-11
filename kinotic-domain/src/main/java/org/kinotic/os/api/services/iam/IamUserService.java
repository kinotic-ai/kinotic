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

    CompletableFuture<Page<IamUser>> findByScope(String authScopeType, String authScopeId, Pageable pageable);

    CompletableFuture<IamUser> createUser(IamUser user, String password);

    CompletableFuture<Void> changePassword(String userId, String currentPassword, String newPassword);

    CompletableFuture<Void> resetPassword(String userId, String newPassword);

}
