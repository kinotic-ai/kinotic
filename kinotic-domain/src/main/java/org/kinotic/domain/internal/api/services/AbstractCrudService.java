package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;

/**
 * Thin {@link IdentifiableCrudService} delegating to an {@link AbstractRepository}. Use this as
 * the service base for entities that are <em>not</em> organization scoped — the system root
 * (e.g. {@code Organization}) and infrastructure-level entities. For organization-scoped data
 * use {@link AbstractOrganizationScopedService} instead, which layers org enforcement on top.
 *
 * Created by Navíd Mitchell 🤪 on 4/24/23.
 */
@RequiredArgsConstructor
public abstract class AbstractCrudService<T extends Identifiable<String>> implements IdentifiableCrudService<T, String> {

    protected final AbstractRepository<T> repository;

    @Override
    public Future<Long> count() {
        return repository.count();
    }

    @Override
    public Future<T> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Future<Void> deleteById(String id) {
        return beforeDelete(id).compose(v -> repository.deleteById(id));
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return beforeDelete(id).compose(v -> repository.deleteByIdSync(id));
    }

    /**
     * Hook run before every delete — {@link #deleteById} and {@link #deleteByIdSync} both
     * call it, so a subclass cannot accidentally guard one delete path and not the other.
     * Override to validate the delete or cascade dependent data; the delete proceeds when
     * the returned future completes.
     */
    protected Future<Void> beforeDelete(String id) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Page<T>> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Override
    public Future<T> save(T value) {
        return beforeSave(value).compose(v -> repository.save(value));
    }

    @Override
    public Future<T> saveSync(T value) {
        return beforeSave(value).compose(v -> repository.saveSync(value));
    }

    @Override
    public Future<T> create(T value) {
        return beforeSave(value).compose(v -> repository.create(value));
    }

    @Override
    public Future<T> createSync(T value) {
        return beforeSave(value).compose(v -> repository.createSync(value));
    }

    /**
     * Hook run before every write — {@link #save}, {@link #saveSync}, {@link #create}, and
     * {@link #createSync} all call it, so a subclass cannot accidentally guard one write
     * path and not the others. Override to validate and prepare the entity (defaults, ids,
     * timestamps); the write proceeds when the returned future completes.
     */
    protected Future<Void> beforeSave(T entity) {
        return Future.succeededFuture();
    }

    @Override
    public Future<Page<T>> search(String searchText, Pageable pageable) {
        return repository.search(searchText, pageable);
    }

    @Override
    public Future<Void> syncIndex() {
        return repository.syncIndex();
    }
}
