package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Repository tier for entities that belong to an organization. Adds {@code orgId}-aware
 * overloads of the base CRUD operations: when supplied the repository
 * <ul>
 *   <li>uses {@code orgId} as the Elasticsearch routing key, and</li>
 *   <li>for {@code count}/{@code findAll}/{@code search} (and the specialized finders on the
 *       intermediate repositories), AND-s an {@code organizationId} term filter onto the query.</li>
 * </ul>
 * The decision of <em>whether</em> to pass an {@code orgId} stays with the service.
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
        return count(b -> applyOrgScope(b::routing, b::query, orgId));
    }

    public CompletableFuture<T> findById(String id, String orgId) {
        return findById(id, orgId != null ? b -> b.routing(orgId) : null);
    }

    public CompletableFuture<Void> deleteById(String id, String orgId) {
        return deleteById(id, orgId != null ? b -> b.routing(orgId) : null);
    }

    public CompletableFuture<Page<T>> findAll(Pageable pageable, String orgId) {
        return findAll(pageable, b -> applyOrgScope(b::routing, b::query, orgId));
    }

    public CompletableFuture<T> save(T value, String orgId) {
        return save(value, orgId != null ? b -> b.routing(orgId) : null);
    }

    public CompletableFuture<T> saveSync(T value, String orgId) {
        return saveSync(value, orgId != null ? b -> b.routing(orgId) : null);
    }

    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable, String orgId) {
        boolean hasText = searchText != null && !searchText.isEmpty();
        if (!hasText) {
            return findAll(pageable, orgId);
        }
        if (orgId == null) {
            return findAll(pageable, b -> b.q(searchText));
        }
        return findAll(pageable, b -> b.routing(orgId).query(Query.of(q -> q.bool(bq -> bq
                .must(m -> m.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                .filter(orgIdTerm(orgId))))));
    }

    /**
     * Builds a bool query whose {@code filter} clauses include any caller-supplied
     * {@code extraFilters} plus an {@code organizationId} term filter when {@code orgId}
     * is non-null. Returns {@code null} when no filter clauses would be added so callers can
     * skip setting a query entirely.
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

    private void applyOrgScope(Consumer<String> routingSetter,
                               Consumer<Query> querySetter,
                               String orgId) {
        if (orgId == null) return;
        routingSetter.accept(orgId);
        Query filter = composeOrgFilter(orgId);
        if (filter != null) querySetter.accept(filter);
    }
}
