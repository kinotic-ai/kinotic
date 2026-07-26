package org.kinotic.idl.internal.support;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/26/26.
 */
public interface GenericCrudService<T, ID> {

    CompletableFuture<T> save(T entity);

    CompletableFuture<T> findById(ID id);

}
