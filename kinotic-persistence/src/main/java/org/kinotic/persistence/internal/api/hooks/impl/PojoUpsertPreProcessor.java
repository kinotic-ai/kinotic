package org.kinotic.persistence.internal.api.hooks.impl;

import io.vertx.core.Future;
import org.apache.commons.lang3.NotImplementedException;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.internal.api.hooks.UpsertPreProcessor;
import org.kinotic.persistence.internal.api.services.EntityHolder;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 6/7/23.
 */
public class PojoUpsertPreProcessor implements UpsertPreProcessor<Object, List<Object>, Object> {

    @Override
    public Future<EntityHolder<Object>> process(Object entity, EntityContext context) {
        throw new NotImplementedException("Pojo upsert is not implemented yet");
    }

    @Override
    public Future<List<EntityHolder<Object>>> processArray(List<Object> entities, EntityContext context) {
        throw new NotImplementedException("Pojo upsert is not implemented yet");
    }
}
