package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Pure Elasticsearch CRUD over a single index. Knows nothing about authentication
 * &mdash; that lives in {@code AbstractCrudService}, which composes a repository instance and
 * adds enforcement on top.
 * <p>
 * Each public CRUD method has a no-arg form for the unscoped case and an overload that
 * accepts an {@code orgId}. When supplied, the repository:
 * <ul>
 *   <li>uses {@code orgId} as the Elasticsearch routing key, and</li>
 *   <li>for {@code count}/{@code findAll}/{@code search} (and the specialized finders on the
 *       intermediate repositories), AND-s an {@code organizationId} term filter onto the query.</li>
 * </ul>
 * The {@code organizationId} filter is only applied when {@code T} implements
 * {@link OrganizationScoped}; for non-org-scoped entities the {@code orgId} parameter is
 * ignored. The decision of <em>whether</em> to pass an {@code orgId} stays with the service.
 * <p>
 * Subclasses that need to issue specialized queries (custom finders) use the protected
 * {@code do*} helpers and {@link #composeOrgFilter} rather than reaching for
 * {@link CrudServiceTemplate} directly.
 *
 * @param <T> the entity type managed by this repository
 */
@RequiredArgsConstructor
public abstract class AbstractRepository<T extends Identifiable<String>> {

    static final String ORGANIZATION_ID_FIELD = "organizationId";

    protected final String indexName;
    @Getter
    protected final Class<T> type;
    protected final ElasticsearchAsyncClient esAsyncClient;
    protected final CrudServiceTemplate crudServiceTemplate;

    private boolean organizationScoped;

    @PostConstruct
    public void verifyIndexExists() {
        this.organizationScoped = OrganizationScoped.class.isAssignableFrom(type);
        crudServiceTemplate.verifyIndexExists(indexName);
    }

    public CompletableFuture<Long> count() {
        return count(null);
    }

    public CompletableFuture<Long> count(String orgId) {
        return doCount(b -> applyOrgScope(b::routing, b::query, orgId, null));
    }

    public CompletableFuture<T> findById(String id) {
        return findById(id, null);
    }

    public CompletableFuture<T> findById(String id, String orgId) {
        return doFindById(id, orgId != null ? b -> b.routing(orgId) : null);
    }

    public CompletableFuture<Void> deleteById(String id) {
        return deleteById(id, null);
    }

    public CompletableFuture<Void> deleteById(String id, String orgId) {
        return doDeleteById(id, orgId != null ? b -> b.routing(orgId) : null);
    }

    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        return findAll(pageable, null);
    }

    public CompletableFuture<Page<T>> findAll(Pageable pageable, String orgId) {
        return doSearch(pageable, b -> applyOrgScope(b::routing, b::query, orgId, null));
    }

    public CompletableFuture<T> save(T value) {
        return save(value, null);
    }

    public CompletableFuture<T> save(T value, String orgId) {
        return doSave(value, orgId != null ? b -> b.routing(orgId) : null);
    }

    public CompletableFuture<T> saveSync(T value) {
        return saveSync(value, null);
    }

    public CompletableFuture<T> saveSync(T value, String orgId) {
        return doSaveSync(value, orgId != null ? b -> b.routing(orgId) : null);
    }

    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        return search(searchText, pageable, null);
    }

    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable, String orgId) {
        boolean hasText = searchText != null && !searchText.isEmpty();
        boolean filterByOrg = orgId != null && organizationScoped;
        if (!hasText) {
            return findAll(pageable, orgId);
        }
        return doSearch(pageable, b -> {
            if (orgId != null) b.routing(orgId);
            if (filterByOrg) {
                b.query(Query.of(q -> q.bool(bq -> bq
                        .must(m -> m.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                        .filter(TermQuery.of(t -> t.field(ORGANIZATION_ID_FIELD).value(orgId))._toQuery()))));
            } else {
                b.q(searchText);
            }
        });
    }

    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
                            .refresh(b -> b.index(indexName))
                            .thenApply(unused -> null);
    }

    /**
     * Builds a bool query whose {@code filter} clauses include any caller-supplied
     * {@code extraFilters} plus an {@code organizationId} term filter when {@code orgId} is
     * non-null and {@code T} implements {@link OrganizationScoped}. Returns {@code null} when
     * no filter clauses would be added so callers can skip setting a query entirely.
     */
    protected Query composeOrgFilter(String orgId, Query... extraFilters) {
        boolean filterByOrg = orgId != null && organizationScoped;
        boolean hasExtras = extraFilters != null && extraFilters.length > 0;
        if (!filterByOrg && !hasExtras) return null;
        return Query.of(q -> q.bool(b -> {
            if (hasExtras) for (Query f : extraFilters) if (f != null) b.filter(f);
            if (filterByOrg) {
                b.filter(TermQuery.of(t -> t.field(ORGANIZATION_ID_FIELD).value(orgId))._toQuery());
            }
            return b;
        }));
    }

    /**
     * Counts documents with full builder access. Subclasses use this to issue specialized
     * count queries without touching {@link CrudServiceTemplate} directly.
     */
    protected CompletableFuture<Long> doCount(Consumer<CountRequest.Builder> builderConsumer) {
        return crudServiceTemplate.count(indexName, builderConsumer);
    }

    protected CompletableFuture<T> doFindById(String id, Consumer<GetRequest.Builder> builderConsumer) {
        return crudServiceTemplate.findById(indexName, id, type, builderConsumer);
    }

    protected CompletableFuture<Void> doDeleteById(String id, Consumer<DeleteRequest.Builder> builderConsumer) {
        return crudServiceTemplate.deleteById(indexName, id, builderConsumer)
                                  .thenApply(response -> null);
    }

    /**
     * Issues a search with full builder access. Subclasses use this to issue specialized
     * paginated queries without touching {@link CrudServiceTemplate} directly.
     */
    protected CompletableFuture<Page<T>> doSearch(Pageable pageable, Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.search(indexName, pageable, type, builderConsumer);
    }

    protected CompletableFuture<T> doSave(T value, Consumer<IndexRequest.Builder<T>> builderConsumer) {
        return crudServiceTemplate.save(indexName, value.getId(), value, builderConsumer)
                                  .thenApply(indexResponse -> value);
    }

    protected CompletableFuture<T> doSaveSync(T value, Consumer<IndexRequest.Builder<T>> builderConsumer) {
        return crudServiceTemplate.saveSync(indexName, value.getId(), value, builderConsumer)
                                  .thenApply(indexResponse -> value);
    }

    /**
     * Multi-gets the given ids and returns those whose source resolved successfully. Missing
     * docs are silently dropped; the order of the returned list matches the order of resolved
     * hits (not necessarily the input order).
     */
    protected CompletableFuture<List<T>> doMultiGetByIds(List<String> ids) {
        List<MultiGetOperation> ops = ids.stream()
                                         .map(id -> MultiGetOperation.of(o -> o.index(indexName).id(id)))
                                         .toList();
        return crudServiceTemplate.<T, T>multiGet(ops, type, null, null)
                                  .thenApply(list -> list.stream().filter(Objects::nonNull).toList());
    }

    private void applyOrgScope(Consumer<String> routingSetter,
                               Consumer<Query> querySetter,
                               String orgId,
                               Query extraFilter) {
        if (orgId != null) routingSetter.accept(orgId);
        Query filter = composeOrgFilter(orgId, extraFilter);
        if (filter != null) querySetter.accept(filter);
    }
}
