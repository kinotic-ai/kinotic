package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.os.api.model.iam.AuthScope;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.internal.api.services.AbstractCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.kinotic.os.internal.model.iam.IamCredential;
import org.kinotic.os.internal.services.iam.IamCredentialStore;
import org.kinotic.os.internal.services.iam.PasswordService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultIamUserService extends AbstractCrudService<IamUser> implements IamUserService {

    private final IamCredentialStore credentialStore;
    private final PasswordService passwordService;

    public DefaultIamUserService(CrudServiceTemplate crudServiceTemplate,
                                 ElasticsearchAsyncClient esAsyncClient,
                                 IamCredentialStore credentialStore,
                                 PasswordService passwordService) {
        super("kinotic_iam_user", IamUser.class, esAsyncClient, crudServiceTemplate);
        this.credentialStore = credentialStore;
        this.passwordService = passwordService;
    }

    @Override
    public CompletableFuture<IamUser> save(IamUser entity) {
        Validate.notNull(entity.getEmail(), "IamUser email cannot be null");
        Validate.notNull(entity.getAuthScopeType(), "IamUser authScopeType cannot be null");
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdated(new Date());
        return super.save(entity);
    }

    @Override
    public CompletableFuture<IamUser> findByEmailAndScope(String email, AuthScope authScopeType, String authScopeId) {
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("email").value(email))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType.name()))._toQuery());
                    if (authScopeId != null) {
                        b.filter(TermQuery.of(t -> t.field("authScopeId").value(authScopeId))._toQuery());
                    } else {
                        b.mustNot(mn -> mn.exists(e -> e.field("authScopeId")));
                    }
                    return b;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    @Override
    public CompletableFuture<Page<IamUser>> findByScope(AuthScope authScopeType, String authScopeId, Pageable pageable) {
        return crudServiceTemplate.search(indexName, pageable, type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType.name()))._toQuery());
                    if (authScopeId != null) {
                        b.filter(TermQuery.of(t -> t.field("authScopeId").value(authScopeId))._toQuery());
                    } else {
                        b.mustNot(mn -> mn.exists(e -> e.field("authScopeId")));
                    }
                    return b;
                }))));
    }

    @Override
    public CompletableFuture<IamUser> createUser(IamUser user, String password) {
        Validate.notNull(user.getEmail(), "IamUser email cannot be null");
        Validate.notNull(user.getAuthScopeType(), "IamUser authScopeType cannot be null");

        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }

        Date now = new Date();
        user.setCreated(now);
        user.setUpdated(now);
        user.setEnabled(true);

        if (user.getAuthType() == null) {
            user.setAuthType(password != null ? AuthType.LOCAL : AuthType.OIDC);
        }

        return findByEmailAndScope(user.getEmail(), user.getAuthScopeType(), user.getAuthScopeId())
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("User with email " + user.getEmail()
                                        + " already exists in scope " + user.getAuthScopeType()
                                        + "/" + user.getAuthScopeId()));
                    }
                    return super.save(user);
                })
                .thenCompose(savedUser -> {
                    if (password != null) {
                        IamCredential credential = new IamCredential()
                                .setId(savedUser.getId())
                                .setPasswordHash(passwordService.hash(password));
                        return credentialStore.save(credential).thenApply(c -> savedUser);
                    }
                    return CompletableFuture.completedFuture(savedUser);
                });
    }

    @Override
    public CompletableFuture<Void> changePassword(String userId, String currentPassword, String newPassword) {
        Validate.notNull(userId, "userId cannot be null");
        Validate.notNull(currentPassword, "currentPassword cannot be null");
        Validate.notNull(newPassword, "newPassword cannot be null");

        return credentialStore.findById(userId)
                .thenCompose(credential -> {
                    if (credential == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("No credential found for user " + userId));
                    }
                    if (!passwordService.verify(currentPassword, credential.getPasswordHash())) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Current password is incorrect"));
                    }
                    credential.setPasswordHash(passwordService.hash(newPassword));
                    return credentialStore.save(credential).thenApply(c -> (Void) null);
                });
    }

    @Override
    public CompletableFuture<Void> resetPassword(String userId, String newPassword) {
        Validate.notNull(userId, "userId cannot be null");
        Validate.notNull(newPassword, "newPassword cannot be null");

        IamCredential credential = new IamCredential()
                .setId(userId)
                .setPasswordHash(passwordService.hash(newPassword));
        return credentialStore.save(credential).thenApply(c -> null);
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return credentialStore.deleteById(id)
                .thenCompose(v -> super.deleteById(id));
    }

}
