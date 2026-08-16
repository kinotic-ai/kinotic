package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import io.vertx.core.Future;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

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
    protected final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    public void verifyIndexExists() {
        crudServiceTemplate.verifyIndexExists(indexName);
    }

    public Future<Long> count() {
        return count(null);
    }

    /**
     * Counts documents with full builder access. Subclasses use this overload to issue
     * specialized count queries.
     */
    protected Future<Long> count(Consumer<CountRequest.Builder> builderConsumer) {
        return crudServiceTemplate.count(indexName, builderConsumer);
    }

    public Future<T> findById(String id) {
        return findById(id, null);
    }

    protected Future<T> findById(String id, Consumer<GetRequest.Builder> builderConsumer) {
        return crudServiceTemplate.findById(indexName, id, type, builderConsumer);
    }

    public Future<Void> deleteById(String id) {
        return deleteById(id, null);
    }

    protected Future<Void> deleteById(String id, Consumer<DeleteRequest.Builder> builderConsumer) {
        return crudServiceTemplate.deleteById(indexName, id, builderConsumer)
                                  .mapEmpty();
    }

    public Future<Void> deleteByIdSync(String id) {
        return deleteByIdSync(id, null);
    }

    protected Future<Void> deleteByIdSync(String id, Consumer<DeleteRequest.Builder> builderConsumer) {
        return crudServiceTemplate.deleteByIdSync(indexName, id, builderConsumer)
                                  .mapEmpty();
    }

    public Future<Page<T>> findAll(Pageable pageable) {
        return findAll(pageable, null);
    }

    /**
     * Issues a paginated search with full builder access. Subclasses use this overload to
     * issue specialized queries.
     */
    protected Future<Page<T>> findAll(Pageable pageable, Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.search(indexName, pageable, type, builderConsumer);
    }

    public Future<T> save(T value) {
        return save(value, null);
    }

    protected Future<T> save(T value, Consumer<IndexRequest.Builder<T>> builderConsumer) {
        return crudServiceTemplate.save(indexName, value.getId(), value, builderConsumer)
                                  .map(value);
    }

    public Future<T> saveSync(T value) {
        return saveSync(value, null);
    }

    protected Future<T> saveSync(T value, Consumer<IndexRequest.Builder<T>> builderConsumer) {
        return crudServiceTemplate.saveSync(indexName, value.getId(), value, builderConsumer)
                                  .map(value);
    }

    /**
     * Persists a new entity, failing if one with the same id already exists. Unlike {@link #save},
     * which overwrites, this fails with {@link org.kinotic.core.api.exceptions.AlreadyExistsException}
     * on an id collision — use it to enforce uniqueness on a caller-assigned id.
     */
    public Future<T> create(T value) {
        return crudServiceTemplate.create(indexName, value.getId(), value)
                                  .map(value);
    }

    /**
     * Persists a new entity like {@link #create}, additionally waiting for it to be visible
     * in search results before returning.
     */
    public Future<T> createSync(T value) {
        return createSync(value, null);
    }

    protected Future<T> createSync(T value, Consumer<IndexRequest.Builder<T>> builderConsumer) {
        return crudServiceTemplate.createSync(indexName, value.getId(), value, builderConsumer)
                                  .map(value);
    }

    public Future<Page<T>> search(String searchText, Pageable pageable) {
        if (searchText == null || searchText.isEmpty()) {
            return findAll(pageable);
        }
        return findAll(pageable, b -> b.q(searchText));
    }

    public Future<Void> syncIndex() {
        return crudServiceTemplate.syncIndex(indexName);
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

    protected Future<T> findFirst(Consumer<SearchRequest.Builder> builderConsumer) {
        return crudServiceTemplate.findFirst(indexName, type, builderConsumer);
    }

}
