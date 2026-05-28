package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.AuthScopeType;
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
                scopeFilter(AuthScopeType.valueOf(authScopeType), authScopeId))));
    }

    public CompletableFuture<IamUser> findFirstByEmailInScopeType(String email, String authScopeType) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", email),
                scopeTypeFilter(AuthScopeType.valueOf(authScopeType)))));
    }

    public CompletableFuture<IamUser> findByEmail(String email) {
        return findFirst(b -> b.query(termFilter("email", email)));
    }

    public CompletableFuture<Page<IamUser>> findByScope(String authScopeType, String authScopeId, Pageable pageable) {
        return findAll(pageable, b -> b.query(scopeFilter(AuthScopeType.valueOf(authScopeType), authScopeId)));
    }

    public CompletableFuture<IamUser> findByOidcIdentityAndScope(String oidcSubject,
                                                                 String oidcConfigId,
                                                                 String authScopeType,
                                                                 String authScopeId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId),
                scopeFilter(AuthScopeType.valueOf(authScopeType), authScopeId))));
    }

    public CompletableFuture<List<IamUser>> findByOidcIdentity(String oidcSubject, String oidcConfigId) {
        return findAll(Pageable.ofSize(100), b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId))))
                .thenApply(Page::getContent);
    }

    /**
     * Builds the scope-narrowing filter for {@code (authScopeType, authScopeId)} against the
     * typed scope fields on the document:
     * <ul>
     *   <li>{@code SYSTEM} → {@code organizationId} must be missing</li>
     *   <li>{@code ORGANIZATION} → {@code organizationId == authScopeId} AND {@code applicationId} must be missing</li>
     *   <li>{@code APPLICATION} → {@code applicationId == authScopeId}</li>
     * </ul>
     * For APPLICATION lookups, callers that need to disambiguate users across orgs sharing
     * the same appId should narrow further by {@code organizationId}; this helper does not
     * because the auth-flow callers don't always carry an org id (TODO: tighten once the
     * APP-login flow plumbs organizationId end-to-end).
     */
    private Query scopeFilter(AuthScopeType type, String scopeId) {
        return switch (type) {
            case SYSTEM -> missingFilter("organizationId");
            case ORGANIZATION -> composeFilter(
                    termFilter("organizationId", scopeId),
                    missingFilter("applicationId"));
            case APPLICATION -> termFilter("applicationId", scopeId);
        };
    }

    /** Same as {@link #scopeFilter} but matches any scope id within {@code type}. */
    private Query scopeTypeFilter(AuthScopeType type) {
        return switch (type) {
            case SYSTEM -> missingFilter("organizationId");
            case ORGANIZATION -> composeFilter(
                    existsFilter("organizationId"),
                    missingFilter("applicationId"));
            case APPLICATION -> existsFilter("applicationId");
        };
    }

    private static Query missingFilter(String field) {
        return Query.of(q -> q.bool(b -> b.mustNot(mn -> mn.exists(e -> e.field(field)))));
    }

    private static Query existsFilter(String field) {
        return Query.of(q -> q.exists(e -> e.field(field)));
    }
}
