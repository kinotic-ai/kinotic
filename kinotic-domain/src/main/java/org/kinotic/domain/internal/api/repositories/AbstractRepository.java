package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
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
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Pure Elasticsearch CRUD over a single index. Knows nothing about authentication
 * or organization scoping &mdash; that lives in {@code AbstractCrudService}, which composes
 * a repository instance and adds enforcement on top.
 * <p>
 * Concrete subclasses supply the {@code indexName} and {@code Class<T>} via the constructor
 * and are registered as Spring beans; services depend on the concrete repository type, not
 * on {@link CrudServiceTemplate} directly.
 * <p>
 * Each CRUD method comes in two flavours: a simple no-arg form for the common case, and a
 * {@code Consumer<...Builder>} form mirroring {@link CrudServiceTemplate} that lets callers
 * customize routing, filters, refresh policy, and any other request option. Repositories
 * deliberately do not implement any {@code CrudService} interface &mdash; those contracts
 * carry an org-scoping guarantee that only the service tier can honour.
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

    private boolean organizationScoped;

    @PostConstruct
    public void verifyIndexExists() {
        this.organizationScoped = OrganizationScoped.class.isAssignableFrom(type);
        crudServiceTemplate.verifyIndexExists(indexName);
    }

    public CompletableFuture<Long> count() {
        return count(null);
    }

    /**
     * Counts documents, letting the caller customize the request (routing, query, etc.).
     *
     * @param builderConsumer to customize the {@link CountRequest.Builder}, or {@code null} for no customization
     */
    public CompletableFuture<Long> count(Consumer<CountRequest.Builder> builderConsumer) {
        return crudServiceTemplate.count(indexName, builderConsumer);
    }

    public CompletableFuture<T> findById(String id) {
        String routing = getRoutingKeyFromId(id);
        return findById(id, routing != null ? b -> b.routing(routing) : null);
    }

    /**
     * Finds a document by id, letting the caller customize the request (routing, source filter, etc.).
     *
     * @param builderConsumer to customize the {@link GetRequest.Builder}, or {@code null} for no customization
     */
    public CompletableFuture<T> findById(String id, Consumer<GetRequest.Builder> builderConsumer) {
        return crudServiceTemplate.findById(indexName, id, type, builderConsumer);
    }

    public CompletableFuture<Void> deleteById(String id) {
        String routing = getRoutingKeyFromId(id);
        return deleteById(id, routing != null ? b -> b.routing(routing) : null);
    }

    /**
     * Deletes a document by id, letting the caller customize the request (routing, refresh, etc.).
     *
     * @param builderConsumer to customize the {@link DeleteRequest.Builder}, or {@code null} for no customization
     */
    public CompletableFuture<Void> deleteById(String id, Consumer<DeleteRequest.Builder> builderConsumer) {
        return crudServiceTemplate.deleteById(indexName, id, builderConsumer)
                                  .thenApply(response -> null);
    }

    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        return findAll(pageable, null);
    }

    /**
     * Returns a page of documents, letting the caller customize the request (routing, query, etc.).
     *
     * @param builderConsumer to customize the {@link SearchRequest.Builder}, or {@code null} for no customization
     */
    public CompletableFuture<Page<T>> findAll(Pageable pageable, Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.search(indexName, pageable, type, builderConsumer);
    }

    public CompletableFuture<T> save(T value) {
        String routing = getObjectRoutingKey(value);
        return save(value, routing != null ? b -> b.routing(routing) : null);
    }

    /**
     * Saves a document, letting the caller customize the request (routing, refresh, version, etc.).
     *
     * @param builderConsumer to customize the {@link IndexRequest.Builder}, or {@code null} for no customization
     */
    public CompletableFuture<T> save(T value, Consumer<IndexRequest.Builder<T>> builderConsumer) {
        return crudServiceTemplate.save(indexName, value.getId(), value, builderConsumer)
                                  .thenApply(indexResponse -> value);
    }

    public CompletableFuture<T> saveSync(T value) {
        String routing = getObjectRoutingKey(value);
        return saveSync(value, routing != null ? b -> b.routing(routing) : null);
    }

    /**
     * Saves a document with {@code Refresh.WaitFor} semantics, letting the caller customize the request.
     *
     * @param builderConsumer to customize the {@link IndexRequest.Builder}, or {@code null} for no customization
     */
    public CompletableFuture<T> saveSync(T value, Consumer<IndexRequest.Builder<T>> builderConsumer) {
        return crudServiceTemplate.saveSync(indexName, value.getId(), value, builderConsumer)
                                  .thenApply(indexResponse -> value);
    }

    /**
     * Convenience full-text search that folds the search text into the request as a top-level
     * {@code q} parameter. For more control over the query (filters, routing, etc.) use
     * {@link #findAll(Pageable, Consumer)} and build the search request directly.
     */
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
     * Override point for repositories whose ids carry a routing prefix. Returns the routing
     * key to use when {@code findById}/{@code deleteById} are called without an explicit
     * consumer. Returning {@code null} (the default) lets Elasticsearch pick the shard by
     * hashing the id.
     */
    protected String getRoutingKeyFromId(String id) {
        return null;
    }

    /**
     * Returns the routing key derived from an entity, used as the default routing on
     * {@code save}/{@code saveSync}. For {@link OrganizationScoped} entities this is the
     * organization id; otherwise {@code null}.
     */
    protected String getObjectRoutingKey(T value) {
        if (organizationScoped) {
            String orgId = ((OrganizationScoped<?>) value).getOrganizationId();
            if (orgId != null && !orgId.isBlank()) {
                return orgId;
            }
        }
        return null;
    }
}
