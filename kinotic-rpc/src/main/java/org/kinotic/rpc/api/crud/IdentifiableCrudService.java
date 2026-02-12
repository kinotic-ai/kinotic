

package org.kinotic.rpc.api.crud;

import org.apache.commons.lang3.Validate;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Extends {@link CrudService} to add a support for types that are {@link Identifiable}
 * Created by navid on 2/3/20
 */
public interface IdentifiableCrudService<T extends Identifiable<ID>, ID> extends CrudService<T, ID> {

    /**
     * Creates a new entity if one does not already exist for the given id
     * @param entity to create if one does not already exist
     * @return a {@link Mono} containing the new entity or an error if an exception occurred
     */
    default CompletableFuture<T> create(T entity) {
        Validate.notNull(entity, "Entity cannot be null");
        ID id = entity.getId();
        if(id != null){
            return findById(entity.getId())
                    .thenCompose(result -> {
                        if (result == null) {
                            return save(entity);
                        } else {
                            CompletableFuture<T> exceptionFuture = new CompletableFuture<>();
                            exceptionFuture.completeExceptionally(new IllegalArgumentException(entity.getClass().getSimpleName() + " for the id " + entity.getId() + " already exists"));
                            return exceptionFuture;
                        }
                    });
        }else{
            return save(entity);
        }
    }


}
