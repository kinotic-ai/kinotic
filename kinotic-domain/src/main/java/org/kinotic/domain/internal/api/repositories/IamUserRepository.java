package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public class IamUserRepository extends AbstractRepository<IamUser> {

    public IamUserRepository(ElasticsearchAsyncClient esAsyncClient,
                             CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_iam_user", IamUser.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<IamUser> findByEmailAndScope(String email, String authScopeType, String authScopeId) {
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, b -> b
                .query(q -> q.bool(BoolQuery.of(bb -> {
                    bb.filter(TermQuery.of(t -> t.field("email").value(email))._toQuery());
                    bb.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType))._toQuery());
                    bb.filter(TermQuery.of(t -> t.field("authScopeId").value(authScopeId))._toQuery());
                    return bb;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    public CompletableFuture<IamUser> findFirstByEmailInScopeType(String email, String authScopeType) {
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, b -> b
                .query(q -> q.bool(BoolQuery.of(bb -> {
                    bb.filter(TermQuery.of(t -> t.field("email").value(email))._toQuery());
                    bb.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType))._toQuery());
                    return bb;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    public CompletableFuture<IamUser> findByEmail(String email) {
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, b -> b
                .query(q -> q.term(t -> t.field("email").value(email))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    public CompletableFuture<Page<IamUser>> findByScope(String authScopeType, String authScopeId, Pageable pageable) {
        return crudServiceTemplate.search(indexName, pageable, type, b -> b
                .query(q -> q.bool(BoolQuery.of(bb -> {
                    bb.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType))._toQuery());
                    bb.filter(TermQuery.of(t -> t.field("authScopeId").value(authScopeId))._toQuery());
                    return bb;
                }))));
    }

    public CompletableFuture<IamUser> findByOidcIdentityAndScope(String oidcSubject,
                                                                 String oidcConfigId,
                                                                 String authScopeType,
                                                                 String authScopeId) {
        return crudServiceTemplate.search(indexName, Pageable.create(0, 1, Sort.unsorted()), type, b -> b
                .query(q -> q.bool(BoolQuery.of(bb -> {
                    bb.filter(TermQuery.of(t -> t.field("oidcSubject").value(oidcSubject))._toQuery());
                    bb.filter(TermQuery.of(t -> t.field("oidcConfigId").value(oidcConfigId))._toQuery());
                    bb.filter(TermQuery.of(t -> t.field("authScopeType").value(authScopeType))._toQuery());
                    bb.filter(TermQuery.of(t -> t.field("authScopeId").value(authScopeId))._toQuery());
                    return bb;
                }))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    public CompletableFuture<List<IamUser>> findByOidcIdentity(String oidcSubject, String oidcConfigId) {
        return crudServiceTemplate.search(indexName, Pageable.create(0, 100, Sort.unsorted()), type, b -> b
                .query(q -> q.bool(BoolQuery.of(bb -> {
                    bb.filter(TermQuery.of(t -> t.field("oidcSubject").value(oidcSubject))._toQuery());
                    bb.filter(TermQuery.of(t -> t.field("oidcConfigId").value(oidcConfigId))._toQuery());
                    return bb;
                }))))
                .thenApply(Page::getContent);
    }
}
