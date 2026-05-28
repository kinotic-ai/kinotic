package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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

    public CompletableFuture<IamUser> findByEmail(String email, String organizationId, String applicationId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", email),
                scopeFilter(organizationId, applicationId))));
    }

    public CompletableFuture<IamUser> findFirstOrgUserByEmail(String email) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("email", email),
                existsFilter("organizationId"),
                missingFilter("applicationId"))));
    }

    public CompletableFuture<IamUser> findByEmail(String email) {
        return findFirst(b -> b.query(termFilter("email", email)));
    }

    public CompletableFuture<Page<IamUser>> findByScope(String organizationId, String applicationId, Pageable pageable) {
        return findAll(pageable, b -> b.query(scopeFilter(organizationId, applicationId)));
    }

    public CompletableFuture<IamUser> findByOidcIdentity(String oidcSubject,
                                                         String oidcConfigId,
                                                         String organizationId,
                                                         String applicationId) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId),
                scopeFilter(organizationId, applicationId))));
    }

    public CompletableFuture<List<IamUser>> findAllByOidcIdentity(String oidcSubject, String oidcConfigId) {
        return findAll(Pageable.ofSize(100), b -> b.query(composeFilter(
                termFilter("oidcSubject", oidcSubject),
                termFilter("oidcConfigId", oidcConfigId))))
                .thenApply(Page::getContent);
    }

    /**
     * Structural scope filter against the typed scope fields on the document:
     * <ul>
     *   <li>both null → {@code organizationId} must be missing (SYSTEM)</li>
     *   <li>only {@code organizationId} set → matches that org AND {@code applicationId} is missing (ORG)</li>
     *   <li>both set → matches both fields (APP)</li>
     * </ul>
     */
    private Query scopeFilter(String organizationId, String applicationId) {
        if (organizationId == null && applicationId == null) {
            return missingFilter("organizationId");
        }
        if (applicationId == null) {
            return composeFilter(
                    termFilter("organizationId", organizationId),
                    missingFilter("applicationId"));
        }
        return composeFilter(
                termFilter("organizationId", organizationId),
                termFilter("applicationId", applicationId));
    }

    private static Query missingFilter(String field) {
        return Query.of(q -> q.bool(b -> b.mustNot(mn -> mn.exists(e -> e.field(field)))));
    }

    private static Query existsFilter(String field) {
        return Query.of(q -> q.exists(e -> e.field(field)));
    }
}
