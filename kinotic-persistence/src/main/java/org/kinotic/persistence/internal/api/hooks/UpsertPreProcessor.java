package org.kinotic.persistence.internal.api.hooks;

import io.vertx.core.Future;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.internal.api.services.EntityHolder;

import java.util.List;

/**
 * Performs logic on Entity Objects before they are upserted.
 *
 * Created by Navíd Mitchell 🤪 on 5/5/23.
 */
public interface UpsertPreProcessor<T, ARRAY_TYPE, ENTITY_TYPE> {

    /**
     * Processes a single entity before it is upserted.
     * This applies all decorator logic to fields that have decorators.
     * @param entity to process
     * @param context the context for this operation
     * @return {@link Future} emitting the processed entity
     */
    Future<EntityHolder<ENTITY_TYPE>> process(T entity, EntityContext context);

    /**
     * Processes an array of entities before they are upserted.
     * This applies all decorator logic to fields that have decorators.
     * @param entities to process
     * @param context the context for this operation
     * @return {@link Future} emitting the processed entities
     */
    Future<List<EntityHolder<ENTITY_TYPE>>> processArray(ARRAY_TYPE entities, EntityContext context);

}
