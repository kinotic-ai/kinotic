package org.kinotic.idl.internal.support;

import org.kinotic.idl.api.annotations.McpToolInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 7/26/26.
 */
public interface GenericCrudService<T, ID> {

    @McpToolInfo(title = "Store Entity", idempotentHint = true)
    CompletableFuture<T> save(T entity);

    /**
     * Finds the entity with the given id.
     */
    CompletableFuture<T> findById(ID id);

}
