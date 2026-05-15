package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Repository tier for entities that belong to an organization. Adds {@code orgId}-aware
 * overloads of the base CRUD operations: the repository uses {@code orgId} as the
 * Elasticsearch routing key, and for {@code count}/{@code findAll}/{@code search} (and the
 * specialized finders on the intermediate repositories) AND-s an {@code organizationId} term
 * filter onto the query.
 * <p>
 * All {@code orgId} parameters are required; passing {@code null} throws
 * {@link NullPointerException}. Callers that genuinely don't have an organization context
 * (e.g. operating under elevated access) should use the inherited no-arg base CRUD methods.
 */
public abstract class AbstractOrganizationScopedRepository<T extends OrganizationScoped<String>>
        extends AbstractRepository<T> {

    static final String ORGANIZATION_ID_FIELD = "organizationId";

    public AbstractOrganizationScopedRepository(String indexName,
                                                Class<T> type,
                                                ElasticsearchAsyncClient esAsyncClient,
                                                CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<Long> count(String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return count(b -> b.routing(orgId).query(composeOrgFilter(orgId)));
    }

    public CompletableFuture<T> findById(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findById(id, b -> b.routing(orgId));
    }

    public CompletableFuture<Void> deleteById(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return deleteById(id, b -> b.routing(orgId));
    }

    public CompletableFuture<Page<T>> findAll(Pageable pageable, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(pageable, b -> b.routing(orgId).query(composeOrgFilter(orgId)));
    }

    public CompletableFuture<T> save(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return save(value, b -> b.routing(orgId));
    }

    public CompletableFuture<T> saveSync(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return saveSync(value, b -> b.routing(orgId));
    }

    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        boolean hasText = searchText != null && !searchText.isEmpty();
        if (!hasText) {
            return findAll(pageable, orgId);
        }
        return findAll(pageable, b -> b.routing(orgId).query(Query.of(q -> q.bool(bq -> bq
                .must(m -> m.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                .filter(orgIdTerm(orgId))))));
    }

    /**
     * Builds a bool query whose {@code filter} clauses include any caller-supplied
     * {@code extraFilters} plus an {@code organizationId} term filter when {@code orgId}
     * is non-null. Returns {@code null} when no filter clauses would be added so callers can
     * skip setting a query entirely. Internal helper &mdash; public overloads pass non-null
     * {@code orgId}, intermediate-tier overloads may pass {@code null} when scoping by another
     * field only (e.g. {@code applicationId}).
     */
    protected Query composeOrgFilter(String orgId, Query... extraFilters) {
        boolean filterByOrg = orgId != null;
        boolean hasExtras = extraFilters != null && extraFilters.length > 0;
        if (!filterByOrg && !hasExtras) return null;
        return Query.of(q -> q.bool(b -> {
            if (hasExtras) for (Query f : extraFilters) if (f != null) b.filter(f);
            if (filterByOrg) b.filter(orgIdTerm(orgId));
            return b;
        }));
    }

    private static Query orgIdTerm(String orgId) {
        return TermQuery.of(t -> t.field(ORGANIZATION_ID_FIELD).value(orgId))._toQuery();
    }
}
