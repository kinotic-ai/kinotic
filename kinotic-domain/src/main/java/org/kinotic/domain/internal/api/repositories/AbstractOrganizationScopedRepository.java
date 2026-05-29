package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Repository tier for entities that belong to an organization. Every public operation is
 * scoped to a supplied {@code orgId}: only documents that belong to that org are returned,
 * mutated, or counted.
 * <p>
 * Documents are stored with a composite Elasticsearch {@code _id} of {@code orgId + "-" + id},
 * mirroring the {@code SHARED} multi-tenancy pattern used by
 * {@link org.kinotic.persistence.internal.api.services.EntityHolder}. The composite id makes
 * the stored document globally unique across orgs, so a get-by-id under one org cannot
 * return another org's document when the two routing values hash to the same shard. The
 * entity's own {@code id} is not modified — the namespacing only happens at the persistence
 * layer, and the raw id round-trips through the source.
 * <p>
 * Composition (rather than inheritance from {@link AbstractRepository}) is intentional: this
 * class deliberately does NOT expose the unscoped {@code findById}/{@code deleteById}/
 * {@code save}/{@code saveSync} forms, because under the composite-id scheme a lookup or
 * write without {@code orgId} is structurally unable to address the right document and would
 * be a foot-gun for callers.
 */
@RequiredArgsConstructor
public abstract class AbstractOrganizationScopedRepository<T extends OrganizationScoped<String>> {

    static final String ORGANIZATION_ID_FIELD = "organizationId";

    protected final String indexName;
    @Getter
    protected final Class<T> type;
    protected final ElasticsearchAsyncClient esAsyncClient;
    protected final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(indexName);
    }

    public CompletableFuture<Long> count(String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.count(indexName, b -> b.routing(orgId).query(composeOrgFilter(orgId)));
    }

    /**
     * Returns the document with the given {@code id} that belongs to {@code orgId}, or
     * {@code null} if no such document exists.
     */
    public CompletableFuture<T> findById(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.findById(indexName,
                                            composeDocumentId(id, orgId),
                                            type,
                                            b -> b.routing(orgId));
    }

    /**
     * Deletes the document with the given {@code id} that belongs to {@code orgId}. No-op
     * if no such document exists.
     */
    public CompletableFuture<Void> deleteById(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.deleteById(indexName,
                                              composeDocumentId(id, orgId),
                                              b -> b.routing(orgId))
                                  .thenApply(response -> null);
    }

    public CompletableFuture<Page<T>> findAll(String orgId, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.search(indexName, pageable, type,
                                          b -> b.routing(orgId).query(composeOrgFilter(orgId)));
    }

    /**
     * Saves {@code value} as belonging to {@code orgId}. Throws if the entity's own
     * {@code organizationId} disagrees with {@code orgId}.
     */
    public CompletableFuture<T> save(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        requireOrgMatchesEntity(value, orgId);
        return crudServiceTemplate.save(indexName,
                                        composeDocumentId(value.getId(), orgId),
                                        value,
                                        b -> b.routing(orgId))
                                  .thenApply(indexResponse -> value);
    }

    public CompletableFuture<T> saveSync(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        requireOrgMatchesEntity(value, orgId);
        return crudServiceTemplate.saveSync(indexName,
                                            composeDocumentId(value.getId(), orgId),
                                            value,
                                            b -> b.routing(orgId))
                                  .thenApply(indexResponse -> value);
    }

    public CompletableFuture<Page<T>> search(String searchText, String orgId, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        boolean hasText = searchText != null && !searchText.isEmpty();
        if (!hasText) {
            return findAll(orgId, pageable);
        }
        return crudServiceTemplate.search(indexName, pageable, type,
                                          b -> b.routing(orgId).query(Query.of(q -> q.bool(bq -> bq
                                                  .must(m -> m.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                                                  .filter(termFilter(ORGANIZATION_ID_FIELD, orgId))))));
    }

    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
                            .refresh(b -> b.index(indexName))
                            .thenApply(unused -> null);
    }

    protected CompletableFuture<Long> count(Consumer<CountRequest.Builder> builderConsumer) {
        return crudServiceTemplate.count(indexName, builderConsumer);
    }

    protected CompletableFuture<Page<T>> findAll(Pageable pageable, Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.search(indexName, pageable, type, builderConsumer);
    }

    protected CompletableFuture<T> findFirst(Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.findFirst(indexName, type, builderConsumer);
    }

    protected Query composeFilter(Query... filters) {
        return crudServiceTemplate.composeFilter(filters);
    }

    protected Query termFilter(String field, String value) {
        return crudServiceTemplate.termFilter(field, value);
    }

    protected Query termFilter(String field, boolean value) {
        return crudServiceTemplate.termFilter(field, value);
    }

    protected Query termFilter(String field, long value) {
        return crudServiceTemplate.termFilter(field, value);
    }

    protected Query termFilter(String field, double value) {
        return crudServiceTemplate.termFilter(field, value);
    }

    /**
     * Builds a bool query whose {@code filter} clauses are any caller-supplied
     * {@code extraFilters} AND an {@code organizationId} term filter. Subclasses use this to
     * compose specialized queries that should be org-scoped.
     */
    protected Query composeOrgFilter(String orgId, Query... extraFilters) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return Query.of(q -> q.bool(b -> {
            if (extraFilters != null) for (Query f : extraFilters) if (f != null) b.filter(f);
            b.filter(termFilter(ORGANIZATION_ID_FIELD, orgId));
            return b;
        }));
    }

    private void requireOrgMatchesEntity(T value, String orgId) {
        String entityOrgId = value.getOrganizationId();
        Validate.isTrue(orgId.equals(entityOrgId),
                        "Cannot save %s whose organizationId '%s' does not match orgId '%s'",
                        getType().getSimpleName(), entityOrgId, orgId);
    }

    /**
     * Builds the Elasticsearch {@code _id} for an entity belonging to {@code orgId}:
     * {@code orgId + "-" + id}. Subclasses use this when issuing specialized queries that
     * target documents by id (e.g. an {@code IdsQuery}) and therefore need the composite
     * form rather than the entity's raw id.
     */
    protected String composeDocumentId(String id, String orgId) {
        Validate.notBlank(id, "id cannot be blank");
        return orgId + "-" + id;
    }
}
