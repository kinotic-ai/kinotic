package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
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
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Pure Elasticsearch CRUD over a single index. Knows nothing about authentication, organization
 * scoping, or any other business concern &mdash; it just maps typed CRUD calls onto the index
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

    /**
     * Multi-gets the given ids and returns those whose source resolved successfully. Missing
     * docs are silently dropped; the order of the returned list matches the order of resolved
     * hits (not necessarily the input order).
     */
    protected CompletableFuture<List<T>> multiGetByIds(List<String> ids) {
        List<MultiGetOperation> ops = ids.stream()
                                         .map(id -> MultiGetOperation.of(o -> o.index(indexName).id(id)))
                                         .toList();
        return crudServiceTemplate.<T, T>multiGet(ops, type, null, null)
                                  .thenApply(list -> list.stream().filter(Objects::nonNull).toList());
    }
}
