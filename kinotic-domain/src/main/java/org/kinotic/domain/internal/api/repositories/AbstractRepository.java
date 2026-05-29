package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Pure Elasticsearch CRUD over a single index. Knows nothing about authentication, organization
 * scoping, or any other business concern — it just maps typed CRUD calls onto the index
 * named in the constructor.
 * <p>
 * Each base CRUD method has a public no-arg form and a protected {@code Consumer<...Builder>}
 * overload that subclasses use to issue specialized queries without reaching for
 * {@link CrudServiceTemplate} directly.
 *
 * @param <T> the entity type managed by this repository
 */
@RequiredArgsConstructor
public abstract class AbstractRepository<T extends Identifiable<String>> {

    protected final String indexName;
    @Getter
    protected final Class<T> type;
    protected final ElasticsearchAsyncClient esAsyncClient;
    protected final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(indexName);
    }

    public CompletableFuture<Long> count() {
        return count(null);
    }

    /**
     * Counts documents with full builder access. Subclasses use this overload to issue
     * specialized count queries.
     */
    protected CompletableFuture<Long> count(Consumer<CountRequest.Builder> builderConsumer) {
        return crudServiceTemplate.count(indexName, builderConsumer);
    }

    public CompletableFuture<T> findById(String id) {
        return findById(id, null);
    }

    protected CompletableFuture<T> findById(String id, Consumer<GetRequest.Builder> builderConsumer) {
        return crudServiceTemplate.findById(indexName, id, type, builderConsumer);
    }

    public CompletableFuture<Void> deleteById(String id) {
        return deleteById(id, null);
    }

    protected CompletableFuture<Void> deleteById(String id, Consumer<DeleteRequest.Builder> builderConsumer) {
        return crudServiceTemplate.deleteById(indexName, id, builderConsumer)
                                  .thenApply(response -> null);
    }

    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        return findAll(pageable, null);
    }

    /**
     * Issues a paginated search with full builder access. Subclasses use this overload to
     * issue specialized queries.
     */
    protected CompletableFuture<Page<T>> findAll(Pageable pageable, Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.search(indexName, pageable, type, builderConsumer);
    }

    public CompletableFuture<T> save(T value) {
        return save(value, null);
    }

    protected CompletableFuture<T> save(T value, Consumer<IndexRequest.Builder<T>> builderConsumer) {
        return crudServiceTemplate.save(indexName, value.getId(), value, builderConsumer)
                                  .thenApply(indexResponse -> value);
    }

    public CompletableFuture<T> saveSync(T value) {
        return saveSync(value, null);
    }

    protected CompletableFuture<T> saveSync(T value, Consumer<IndexRequest.Builder<T>> builderConsumer) {
        return crudServiceTemplate.saveSync(indexName, value.getId(), value, builderConsumer)
                                  .thenApply(indexResponse -> value);
    }

    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        if (searchText == null || searchText.isEmpty()) {
            return findAll(pageable);
        }
        return findAll(pageable, b -> b.q(searchText));
    }

    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
                            .refresh(b -> b.index(indexName))
                            .thenApply(unused -> null);
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

    protected Query existsFilter(String field) {
        return crudServiceTemplate.existsFilter(field);
    }

    protected Query missingFilter(String field) {
        return crudServiceTemplate.missingFilter(field);
    }

    protected CompletableFuture<T> findFirst(Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.findFirst(indexName, type, builderConsumer);
    }

}
