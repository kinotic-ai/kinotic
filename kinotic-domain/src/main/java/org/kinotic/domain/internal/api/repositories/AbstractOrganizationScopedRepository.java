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
 * overloads of the base CRUD operations: every operation is scoped to the supplied
 * organization, returning, mutating, or counting only documents that belong to it.
 * <p>
 * All {@code orgId} parameters are required; passing {@code null} or blank throws. Callers
 * that genuinely don't have an organization context (e.g. operating under elevated access)
 * should use the inherited no-arg base CRUD methods.
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

    /**
     * Returns the document with the given {@code id} that belongs to {@code orgId}, or
     * {@code null} if no such document exists.
     */
    public CompletableFuture<T> findById(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findById(id, b -> b.routing(orgId))
                .thenApply(value -> {
                    if (value == null) return null;
                    // routing only narrows to a shard; with small shard counts two orgs can
                    // hash to the same shard, so a Get can return a doc indexed under another
                    // org's routing. Drop it.
                    if (!orgId.equals(value.getOrganizationId())) return null;
                    return value;
                });
    }

    /**
     * Deletes the document with the given {@code id} that belongs to {@code orgId}. No-op
     * if no such document exists.
     */
    public CompletableFuture<Void> deleteById(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        // findById's post-fetch check already enforces the org contract; if it returns a
        // value, the doc is verified to belong to orgId and is safe to delete.
        return findById(id, orgId).thenCompose(value -> {
            if (value == null) return CompletableFuture.completedFuture(null);
            return deleteById(id, b -> b.routing(orgId));
        });
    }

    public CompletableFuture<Page<T>> findAll(Pageable pageable, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(pageable, b -> b.routing(orgId).query(composeOrgFilter(orgId)));
    }

    /**
     * Saves {@code value} as belonging to {@code orgId}. Throws if the entity's own
     * {@code organizationId} disagrees with {@code orgId}.
     */
    public CompletableFuture<T> save(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        requireOrgMatchesEntity(value, orgId);
        return save(value, b -> b.routing(orgId));
    }

    public CompletableFuture<T> saveSync(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        requireOrgMatchesEntity(value, orgId);
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

    private void requireOrgMatchesEntity(T value, String orgId) {
        String entityOrgId = value.getOrganizationId();
        Validate.isTrue(orgId.equals(entityOrgId),
                        "Cannot save %s whose organizationId '%s' does not match orgId '%s'",
                        getType().getSimpleName(), entityOrgId, orgId);
    }

    /**
     * Builds a bool query whose {@code filter} clauses are any caller-supplied
     * {@code extraFilters} AND an {@code organizationId} term filter. Subclasses use this to
     * compose specialized queries that should be org-scoped. For queries that should NOT be
     * org-scoped (e.g. an intermediate repository's no-arg overload) use
     * {@link #composeFilter} on the base class instead.
     */
    protected Query composeOrgFilter(String orgId, Query... extraFilters) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return Query.of(q -> q.bool(b -> {
            if (extraFilters != null) for (Query f : extraFilters) if (f != null) b.filter(f);
            b.filter(orgIdTerm(orgId));
            return b;
        }));
    }

    private static Query orgIdTerm(String orgId) {
        return TermQuery.of(t -> t.field(ORGANIZATION_ID_FIELD).value(orgId))._toQuery();
    }
}
