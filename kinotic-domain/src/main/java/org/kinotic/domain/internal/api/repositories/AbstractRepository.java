package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Pure Elasticsearch CRUD over a single index. Knows nothing about authentication
 * or organization scoping &mdash; that lives in {@code AbstractCrudService}, which composes
 * a repository instance and adds enforcement on top.
 * <p>
 * Concrete subclasses supply the {@code indexName} and {@code Class<T>} via the constructor
 * and are registered as Spring beans; services depend on the concrete repository type, not
 * on {@link CrudServiceTemplate} directly.
 * <p>
 * Repositories deliberately do not implement any {@code CrudService} interface &mdash; those
 * contracts carry an org-scoping guarantee that only the service tier can honour.
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
        return count(null, null);
    }

    /**
     * Counts documents with optional routing and an optional extra filter.
     *
     * @param routing routing key, or {@code null} for the default routing
     * @param filter  query filter to apply, or {@code null} to count all documents
     */
    public CompletableFuture<Long> count(String routing, Query filter) {
        return crudServiceTemplate.count(indexName, b -> {
            if (routing != null) b.routing(routing);
            if (filter != null) b.query(filter);
        });
    }

    public CompletableFuture<T> findById(String id) {
        return findById(id, getRoutingKeyFromId(id));
    }

    /**
     * Finds a document by id using the supplied routing key.
     *
     * @param routing routing key, or {@code null} for the default routing
     */
    public CompletableFuture<T> findById(String id, String routing) {
        return crudServiceTemplate.findById(indexName, id, type,
                                            routing != null ? b -> b.routing(routing) : null);
    }

    public CompletableFuture<Void> deleteById(String id) {
        return deleteById(id, getRoutingKeyFromId(id));
    }

    /**
     * Deletes a document by id using the supplied routing key.
     *
     * @param routing routing key, or {@code null} for the default routing
     */
    public CompletableFuture<Void> deleteById(String id, String routing) {
        return crudServiceTemplate.deleteById(indexName, id,
                                              routing != null ? b -> b.routing(routing) : null)
                                  .thenApply(response -> null);
    }

    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        return findAll(pageable, null, null);
    }

    /**
     * Returns a page of documents with optional routing and an optional extra filter.
     */
    public CompletableFuture<Page<T>> findAll(Pageable pageable, String routing, Query filter) {
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            if (routing != null) b.routing(routing);
            if (filter != null) b.query(filter);
        });
    }

    public CompletableFuture<T> save(T value) {
        return save(value, getObjectRoutingKey(value));
    }

    /**
     * Saves a document using the supplied routing key.
     *
     * @param routing routing key, or {@code null} for the default routing
     */
    public CompletableFuture<T> save(T value, String routing) {
        return crudServiceTemplate.save(indexName, value.getId(), value,
                                        routing != null ? b -> b.routing(routing) : null)
                                  .thenApply(indexResponse -> value);
    }

    public CompletableFuture<T> saveSync(T value) {
        return saveSync(value, getObjectRoutingKey(value));
    }

    /**
     * Saves a document with {@code Refresh.WaitFor} semantics using the supplied routing key.
     *
     * @param routing routing key, or {@code null} for the default routing
     */
    public CompletableFuture<T> saveSync(T value, String routing) {
        return crudServiceTemplate.saveSync(indexName, value.getId(), value,
                                            routing != null ? b -> b.routing(routing) : null)
                                  .thenApply(indexResponse -> value);
    }

    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        return search(searchText, pageable, null, null);
    }

    /**
     * Full-text search with optional routing and an optional extra filter. When a filter is
     * supplied the search text is folded into the bool query as a {@code queryString} must clause;
     * otherwise the search text is applied as a top-level {@code q} parameter.
     */
    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable, String routing, Query filter) {
        if (filter == null) {
            return crudServiceTemplate.search(indexName, pageable, type,
                                              searchText != null && !searchText.isEmpty()
                                                      ? b -> {
                                                          if (routing != null) b.routing(routing);
                                                          b.q(searchText);
                                                      }
                                                      : routing != null ? b -> b.routing(routing) : null);
        }
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            if (routing != null) b.routing(routing);
            b.query(filter);
        });
    }

    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
                            .refresh(b -> b.index(indexName))
                            .thenApply(unused -> null);
    }

    /**
     * Override point for repositories whose ids carry a routing prefix. Returns the routing
     * key to use when {@code findById}/{@code deleteById} are called without an explicit
     * routing argument. Returning {@code null} (the default) lets Elasticsearch pick the shard
     * by hashing the id.
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
