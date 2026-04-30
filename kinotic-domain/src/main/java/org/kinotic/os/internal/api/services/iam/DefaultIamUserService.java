package org.kinotic.os.internal.api.services.iam;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.utils.DomainUtil;
import org.kinotic.os.internal.api.services.AbstractCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.kinotic.os.internal.api.model.iam.IamCredential;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultIamUserService extends AbstractCrudService<IamUser> implements IamUserService {

    private final IamCredentialService credentialStore;

    public DefaultIamUserService(CrudServiceTemplate crudServiceTemplate,
                                 ElasticsearchAsyncClient esAsyncClient,
                                 IamCredentialService credentialStore,
                                 SecurityContext securityContext) {
        super("kinotic_iam_user", IamUser.class, esAsyncClient, crudServiceTemplate, securityContext);
        this.credentialStore = credentialStore;
    }

    @Override
    public CompletableFuture<IamUser> save(IamUser entity) {
        Validate.notNull(entity.getEmail(), "IamUser email cannot be null");
        Validate.notNull(entity.getAuthScopeType(), "IamUser authScopeType cannot be null");
        // tenantId is meaningful only for APPLICATION-scoped users; SYSTEM/ORGANIZATION identities
        // are not tenants and must not carry one.
        if ("APPLICATION".equals(entity.getAuthScopeType())) {
            Validate.notBlank(entity.getTenantId(),
                              "IamUser tenantId is required for APPLICATION-scoped users");
        } else if (entity.getTenantId() != null) {
            throw new IllegalArgumentException(
                    "IamUser tenantId must be null for " + entity.getAuthScopeType() + "-scoped users");
        }
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdated(new Date());
        return super.save(entity);
    }

    @Override
    public CompletableFuture<IamUser> findByEmailAndScope(String email, String authScopeType, String authScopeId) {
        Validate.notNull(authScopeId, "authScopeId cannot be null");
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("email").value(email))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("authScopeId").value(authScopeId))._toQuery());
                    return b;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    @Override
    public CompletableFuture<IamUser> findFirstByEmailInScopeType(String email, String authScopeType) {
        Validate.notBlank(email, "email cannot be blank");
        Validate.notBlank(authScopeType, "authScopeType cannot be blank");
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("email").value(email))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType))._toQuery());
                    return b;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    @Override
    public CompletableFuture<IamUser> findByEmailPrimary(String email) {
        Validate.notBlank(email, "email cannot be blank");
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("email").value(email))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("primary").value(true))._toQuery());
                    return b;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

//    @Override  // commented off the interface — kept for the eventual user-management UI
    public CompletableFuture<Page<IamUser>> findByScope(String authScopeType, String authScopeId, Pageable pageable) {
        Validate.notNull(authScopeId, "authScopeId cannot be null");
        return crudServiceTemplate.search(indexName, pageable, type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("authScopeId").value(authScopeId))._toQuery());
                    return b;
                }))));
    }

    @Override
    public CompletableFuture<IamUser> findByOidcIdentityAndScope(String oidcSubject,
                                                                 String oidcConfigId,
                                                                 String authScopeType,
                                                                 String authScopeId) {
        Validate.notBlank(oidcSubject, "oidcSubject cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(authScopeType, "authScopeType cannot be blank");
        Validate.notNull(authScopeId, "authScopeId cannot be null");
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("oidcSubject").value(oidcSubject))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("oidcConfigId").value(oidcConfigId))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("authScopeId").value(authScopeId))._toQuery());
                    return b;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    @Override
    public CompletableFuture<java.util.List<IamUser>> findByOidcIdentity(String oidcSubject, String oidcConfigId) {
        Validate.notBlank(oidcSubject, "oidcSubject cannot be blank");
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        return crudServiceTemplate.search(indexName, Pageable.create(0, 100, Sort.unsorted()), type, builder -> builder
                .query(q -> q.bool(BoolQuery.of(b -> {
                    b.filter(TermQuery.of(t -> t.field("oidcSubject").value(oidcSubject))._toQuery());
                    b.filter(TermQuery.of(t -> t.field("oidcConfigId").value(oidcConfigId))._toQuery());
                    return b;
                }))))
                .thenApply(Page::getContent);
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
                                .setPasswordHash(DomainUtil.hashPassword(password));
                        return credentialStore.save(credential).thenApply(c -> savedUser);
                    }
                    return CompletableFuture.completedFuture(savedUser);
                });
    }

//    @Override // commented off the interface — kept for the eventual user-management UI
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
                    if (!DomainUtil.verifyPassword(currentPassword, credential.getPasswordHash())) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Current password is incorrect"));
                    }
                    credential.setPasswordHash(DomainUtil.hashPassword(newPassword));
                    return credentialStore.save(credential).thenApply(c -> (Void) null);
                });
    }

//    @Override
    public CompletableFuture<Void> resetPassword(String userId, String newPassword) {
        Validate.notNull(userId, "userId cannot be null");
        Validate.notNull(newPassword, "newPassword cannot be null");

        IamCredential credential = new IamCredential()
                .setId(userId)
                .setPasswordHash(DomainUtil.hashPassword(newPassword));
        return credentialStore.save(credential).thenApply(c -> null);
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return credentialStore.deleteById(id)
                .thenCompose(v -> super.deleteById(id));
    }

}
