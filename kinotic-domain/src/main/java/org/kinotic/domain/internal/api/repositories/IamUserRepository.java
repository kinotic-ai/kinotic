package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class IamUserRepository extends AbstractRepository<IamUser> {

    public IamUserRepository(ElasticsearchAsyncClient esAsyncClient,
                             CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_iam_user", IamUser.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<IamUser> findByEmailAndScope(String email, String authScopeType, String authScopeId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", email),
                termFilter("authScopeType", authScopeType),
                termFilter("authScopeId", authScopeId))));
    }

    public CompletableFuture<IamUser> findFirstByEmailInScopeType(String email, String authScopeType) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", email),
                termFilter("authScopeType", authScopeType))));
    }

    public CompletableFuture<IamUser> findByEmail(String email) {
        return findFirst(b -> b.query(termFilter("email", email)));
    }

    public CompletableFuture<Page<IamUser>> findByScope(String authScopeType, String authScopeId, Pageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(
                termFilter("authScopeType", authScopeType),
                termFilter("authScopeId", authScopeId))));
    }

    public CompletableFuture<IamUser> findByOidcIdentityAndScope(String oidcSubject,
                                                                 String oidcConfigId,
                                                                 String authScopeType,
                                                                 String authScopeId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId),
                termFilter("authScopeType", authScopeType),
                termFilter("authScopeId", authScopeId))));
    }

    public CompletableFuture<List<IamUser>> findByOidcIdentity(String oidcSubject, String oidcConfigId) {
        return findAll(Pageable.ofSize(100), b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId))))
                .thenApply(Page::getContent);
    }
}
