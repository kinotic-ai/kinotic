package org.kinotic.persistence.internal.api.hooks.impl;

import org.apache.commons.lang3.NotImplementedException;
import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.persistence.internal.api.hooks.UpsertPreProcessor;
import org.kinotic.persistence.internal.api.services.EntityHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 6/7/23.
 */
public class PojoUpsertPreProcessor implements UpsertPreProcessor<Object, List<Object>, Object> {

    @Override
    public CompletableFuture<EntityHolder<Object>> process(Object entity, EntityContext context) {
        throw new NotImplementedException("Pojo upsert is not implemented yet");
    }

    @Override
    public CompletableFuture<List<EntityHolder<Object>>> processArray(List<Object> entities, EntityContext context) {
        throw new NotImplementedException("Pojo upsert is not implemented yet");
    }
}
