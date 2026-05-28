package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
 * Documents are stored with a composite Elasticsearch {@code _id} of {@code orgId + "-" + id},
 * mirroring the {@code SHARED} multi-tenancy pattern used by
 * {@link org.kinotic.persistence.internal.api.services.EntityHolder}. The composite id makes
 * the stored document globally unique across orgs, so a get-by-id under one org cannot
 * return another org's document when the two routing values hash to the same shard. The
 * entity's own {@code id} is not modified — the namespacing only happens at the persistence
 * layer, and the raw id round-trips through the source.
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
        return findById(composeDocumentId(id, orgId), b -> b.routing(orgId));
    }

    /**
     * Deletes the document with the given {@code id} that belongs to {@code orgId}. No-op
     * if no such document exists.
     */
    public CompletableFuture<Void> deleteById(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return deleteById(composeDocumentId(id, orgId), b -> b.routing(orgId));
    }

    public CompletableFuture<Page<T>> findAll(String orgId, Pageable pageable) {
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
        return save(composeDocumentId(value.getId(), orgId), value, b -> b.routing(orgId));
    }

    public CompletableFuture<T> saveSync(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        requireOrgMatchesEntity(value, orgId);
        return saveSync(composeDocumentId(value.getId(), orgId), value, b -> b.routing(orgId));
    }

    public CompletableFuture<Page<T>> search(String searchText, String orgId, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        boolean hasText = searchText != null && !searchText.isEmpty();
        if (!hasText) {
            return findAll(orgId, pageable);
        }
        return findAll(pageable, b -> b.routing(orgId).query(Query.of(q -> q.bool(bq -> bq
                .must(m -> m.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                .filter(termFilter(ORGANIZATION_ID_FIELD, orgId))))));
    }

    private void requireOrgMatchesEntity(T value, String orgId) {
        String entityOrgId = value.getOrganizationId();
        Validate.isTrue(orgId.equals(entityOrgId),
                        "Cannot save %s whose organizationId '%s' does not match orgId '%s'",
                        getType().getSimpleName(), entityOrgId, orgId);
    }

    private String composeDocumentId(String id, String orgId) {
        Validate.notBlank(id, "id cannot be blank");
        return orgId + "-" + id;
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
            b.filter(termFilter(ORGANIZATION_ID_FIELD, orgId));
            return b;
        }));
    }
}
