package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/24/23.
 */
@RequiredArgsConstructor
public abstract class AbstractCrudService<T extends Identifiable<String>> implements IdentifiableCrudService<T, String> {

    protected final String indexName;
    protected final Class<T> type;
    protected final ElasticsearchAsyncClient esAsyncClient;
    protected final CrudServiceTemplate crudServiceTemplate;

    @PostConstruct
    public void verifyIndexExists() {
        try {
            boolean exists = esAsyncClient.indices()
                                          .exists(b -> b.index(indexName))
                                          .get()
                                          .value();
            if (!exists) {
                throw new IllegalStateException(
                        "Elasticsearch index '" + indexName + "' does not exist. "
                        + "Did you forget to add a migration in kinotic-migration/src/main/resources/migrations/?");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to verify existence of index '" + indexName + "'", e);
        }
    }

    @Override
    public CompletableFuture<Long> count() {
        return crudServiceTemplate.count(indexName, null);
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return crudServiceTemplate.deleteById(indexName, id, null)
                                  .thenApply(response -> null);
    }

    @Override
    public CompletableFuture<Page<T>> findAll(Pageable pageable) {
        return crudServiceTemplate.search(indexName, pageable, type, null);
    }

    @Override
    public CompletableFuture<T> findById(String id) {
        return crudServiceTemplate.findById(indexName, id, type, null);
    }

    @Override
    public CompletableFuture<T> save(T entity) {
        return esAsyncClient.index(i -> i
                .index(indexName)
                .id(entity.getId())
                .document(entity))
                .thenCompose(indexResponse -> findById(indexResponse.id()));
    }

    @Override
    public CompletableFuture<Page<T>> search(String searchText, Pageable pageable) {
        return crudServiceTemplate.search(indexName, pageable, type, builder -> builder.q(searchText));
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
                            .refresh(b -> b.index(indexName))
                            .thenApply(unused -> null);
    }

    @Override
    public CompletableFuture<T> saveSync(T entity) {
        return esAsyncClient.index(i -> i
                .index(indexName)
                .id(entity.getId())
                .document(entity)
                .refresh(Refresh.WaitFor))
                .thenCompose(indexResponse -> findById(indexResponse.id()));
    }

}
