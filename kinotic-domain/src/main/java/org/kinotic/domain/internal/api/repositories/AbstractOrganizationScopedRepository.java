package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.vertx.core.Future;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Repository tier for entities that belong to an organization. Every public operation is
 * scoped to a supplied {@code orgId}: only documents that belong to that org are returned,
 * mutated, or counted.
 * <p>
 * Documents are stored with a composite Elasticsearch {@code _id} of {@code orgId + "--" + id}. An
 * organization id never contains {@code --}, so every (org, id) pair has its own {@code _id} and a get-by-id
 * under one org cannot reach another org's document, whichever shard the two route to. The entity's own
 * {@code id} is not modified — the namespacing only happens at the persistence layer, and the raw id
 * round-trips through the source.
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
    private static final String ID_FIELD = "id";
    // DomainUtil.validateOrganizationId forbids "--" in an organization id, so the first "--" of a
    // document id always ends the organization id, whatever the entity id holds
    private static final String DOCUMENT_ID_SEPARATOR = "--";

    protected final String indexName;
    @Getter
    protected final Class<T> type;
    protected final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(indexName);
    }

    public Future<Long> count(String orgId) {
        return count(orgId, null);
    }

    /**
     * Counts the documents of {@code orgId} among the given ids; every document when {@code ids} is null, none
     * when it is empty.
     */
    public Future<Long> count(String orgId, Collection<String> ids) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.count(indexName, b -> b.routing(orgId).query(composeOrgFilter(orgId, idsFilter(ids))));
    }

    /**
     * Returns the document with the given {@code id} that belongs to {@code orgId}, or
     * {@code null} if no such document exists.
     */
    public Future<T> findById(String id, String orgId) {
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
    public Future<Void> deleteById(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.deleteById(indexName,
                                              composeDocumentId(id, orgId),
                                              b -> b.routing(orgId))
                                  .mapEmpty();
    }

    /**
     * Deletes the document with the given {@code id} that belongs to {@code orgId}, waiting
     * for the deletion to be visible in search results before returning. No-op if no such
     * document exists.
     */
    public Future<Void> deleteByIdSync(String id, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.deleteByIdSync(indexName,
                                                  composeDocumentId(id, orgId),
                                                  b -> b.routing(orgId))
                                  .mapEmpty();
    }

    public Future<Page<T>> findAll(String orgId, Pageable pageable) {
        return findAll(orgId, null, pageable);
    }

    /**
     * The page of documents of {@code orgId} among the given ids; every document when {@code ids} is null, none
     * when it is empty.
     */
    public Future<Page<T>> findAll(String orgId, Collection<String> ids, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return crudServiceTemplate.search(indexName, pageable, type,
                                          b -> b.routing(orgId).query(composeOrgFilter(orgId, idsFilter(ids))));
    }

    /**
     * Saves {@code value} as belonging to {@code orgId}. Throws if the entity's own
     * {@code organizationId} disagrees with {@code orgId}.
     */
    public Future<T> save(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        requireOrgMatchesEntity(value, orgId);
        return crudServiceTemplate.save(indexName,
                                        composeDocumentId(value.getId(), orgId),
                                        value,
                                        b -> b.routing(orgId))
                                  .map(value);
    }

    public Future<T> saveSync(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        requireOrgMatchesEntity(value, orgId);
        return crudServiceTemplate.saveSync(indexName,
                                            composeDocumentId(value.getId(), orgId),
                                            value,
                                            b -> b.routing(orgId))
                                  .map(value);
    }

    /**
     * Persists a new entity belonging to {@code orgId}, failing with
     * {@link org.kinotic.core.api.exceptions.AlreadyExistsException} if the id is already
     * taken within that organization, instead of overwriting the way {@link #save} would.
     */
    public Future<T> create(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        requireOrgMatchesEntity(value, orgId);
        return crudServiceTemplate.create(indexName,
                                          composeDocumentId(value.getId(), orgId),
                                          value,
                                          b -> b.routing(orgId))
                                  .map(value);
    }

    /**
     * Persists a new entity like {@link #create}, additionally waiting for it to be visible
     * in search results before returning.
     */
    public Future<T> createSync(T value, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        requireOrgMatchesEntity(value, orgId);
        return crudServiceTemplate.createSync(indexName,
                                              composeDocumentId(value.getId(), orgId),
                                              value,
                                              b -> b.routing(orgId))
                                  .map(value);
    }

    public Future<Page<T>> search(String searchText, String orgId, Pageable pageable) {
        return search(searchText, orgId, null, pageable);
    }

    /**
     * The page of documents of {@code orgId} matching the search text, among the given ids; every matching
     * document when {@code ids} is null, none when it is empty.
     */
    public Future<Page<T>> search(String searchText, String orgId, Collection<String> ids, Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        boolean hasText = searchText != null && !searchText.isEmpty();
        if (!hasText) {
            return findAll(orgId, ids, pageable);
        }
        return crudServiceTemplate.search(indexName, pageable, type,
                                          b -> b.routing(orgId).query(Query.of(q -> q.bool(bq -> {
                                              bq.must(m -> m.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                                                .filter(termFilter(ORGANIZATION_ID_FIELD, orgId));
                                              Query idsFilter = idsFilter(ids);
                                              if (idsFilter != null) {
                                                  bq.filter(idsFilter);
                                              }
                                              return bq;
                                          }))));
    }

    public Future<Void> syncIndex() {
        return crudServiceTemplate.syncIndex(indexName);
    }

    protected Future<Long> count(Consumer<CountRequest.Builder> builderConsumer) {
        return crudServiceTemplate.count(indexName, builderConsumer);
    }

    protected Future<Page<T>> findAll(Pageable pageable, Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.search(indexName, pageable, type, builderConsumer);
    }

    protected Future<T> findFirst(Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.findFirst(indexName, type, builderConsumer);
    }

    protected Query composeFilter(Query... filters) {
        return crudServiceTemplate.composeFilter(filters);
    }

    protected Query termFilter(String field, String value) {
        return crudServiceTemplate.termFilter(field, value);
    }

    /**
     * A filter keeping the documents whose entity id is among the given ones: null for no filter when
     * {@code ids} is null, a filter matching nothing when it is empty.
     */
    protected Query idsFilter(Collection<String> ids) {
        return ids == null ? null : termsFilter(ID_FIELD, ids);
    }

    protected Query termsFilter(String field, Collection<String> values) {
        List<FieldValue> fieldValues = values.stream().map(FieldValue::of).toList();
        return Query.of(q -> q.terms(t -> t.field(field).terms(v -> v.value(fieldValues))));
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
     * {@code orgId + "--" + id}. Subclasses use this when issuing specialized queries that
     * target documents by id (e.g. an {@code IdsQuery}) and therefore need the composite
     * form rather than the entity's raw id.
     */
    protected String composeDocumentId(String id, String orgId) {
        Validate.notBlank(id, "id cannot be blank");
        return orgId + DOCUMENT_ID_SEPARATOR + id;
    }
}
