package org.kinotic.core.api.crud;

import io.vertx.core.Future;

/**
 * Extends {@link CrudService} to add a support for types that are {@link Identifiable}
 * Created by navid on 2/3/20
 */
public interface IdentifiableCrudService<T extends Identifiable<ID>, ID> extends CrudService<T, ID> {

    /**
     * Creates a new entity, failing if one with the same id already exists. Unlike
     * {@link #save}, which overwrites, this fails with
     * {@link org.kinotic.core.api.exceptions.AlreadyExistsException} on an id collision.
     *
     * @param entity to create, must not be {@literal null}
     * @return a {@link Future} containing the new entity, or failing with
     *         {@link org.kinotic.core.api.exceptions.AlreadyExistsException} if the id is
     *         already taken
     */
    Future<T> create(T entity);

    /**
     * Creates a new entity like {@link #create}, additionally waiting for it to be visible
     * in search results before returning. Use this when you need read-your-write consistency
     * immediately after creation.
     *
     * @param entity to create, must not be {@literal null}
     * @return a {@link Future} containing the new entity after it is searchable,
     *         or failing with {@link org.kinotic.core.api.exceptions.AlreadyExistsException}
     *         if the id is already taken
     */
    Future<T> createSync(T entity);

}
