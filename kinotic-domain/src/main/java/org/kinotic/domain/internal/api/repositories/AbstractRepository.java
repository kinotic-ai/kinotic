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
 * Subclasses that need to issue specialized queries (custom finders) use the protected
 * {@code do*} helpers rather than reaching for {@link CrudServiceTemplate} directly.
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
        return doCount(null);
    }

    public CompletableFuture<T> findById(String id) {
        return doFindById(id, null);
    }

    public CompletableFuture<Void> deleteById(String id) {
        return doDeleteById(id, null);
    }

    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        return doSearch(pageable, null);
    }

    public CompletableFuture<T> save(T value) {
        return doSave(value, null);
    }

    public CompletableFuture<T> saveSync(T value) {
        return doSaveSync(value, null);
    }

    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        if (searchText == null || searchText.isEmpty()) {
            return findAll(pageable);
        }
        return doSearch(pageable, b -> b.q(searchText));
    }

    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
                            .refresh(b -> b.index(indexName))
                            .thenApply(unused -> null);
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
}
