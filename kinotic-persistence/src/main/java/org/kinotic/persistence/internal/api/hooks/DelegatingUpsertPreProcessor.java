package org.kinotic.persistence.internal.api.hooks;

import org.apache.commons.lang3.Validate;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.model.EntityDefinition;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.os.api.model.RawJson;
import org.kinotic.persistence.internal.api.hooks.impl.MapUpsertPreProcessor;
import org.kinotic.persistence.internal.api.hooks.impl.PojoUpsertPreProcessor;
import org.kinotic.persistence.internal.api.hooks.impl.RawJsonUpsertPreProcessor;
import org.kinotic.persistence.internal.api.hooks.impl.TokenBufferUpsertPreProcessor;
import org.kinotic.persistence.internal.api.services.EntityHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 6/7/23.
 */
public class DelegatingUpsertPreProcessor implements UpsertPreProcessor<Object, Object, Object> {

    private final TokenBufferUpsertPreProcessor tokenBufferUpsertPreProcessor;
    private final RawJsonUpsertPreProcessor rawJsonUpsertPreProcessor;
    private final MapUpsertPreProcessor mapUpsertPreProcessor;
    private final PojoUpsertPreProcessor pojoUpsertPreProcessor;

    public DelegatingUpsertPreProcessor(PersistenceProperties persistenceProperties,
                                        JsonMapper jsonMapper,
                                        EntityDefinition entityDefinition,
                                        Map<String, DecoratorLogic> fieldPreProcessors) {

        tokenBufferUpsertPreProcessor = new TokenBufferUpsertPreProcessor(persistenceProperties,
                                                                          jsonMapper,
                                                                          entityDefinition,
                                                                          fieldPreProcessors);

        rawJsonUpsertPreProcessor = new RawJsonUpsertPreProcessor(persistenceProperties,
                                                                  jsonMapper,
                                                                  entityDefinition,
                                                                  fieldPreProcessors);

        mapUpsertPreProcessor = new MapUpsertPreProcessor(entityDefinition,
                                                          persistenceProperties,
                                                          fieldPreProcessors);
        pojoUpsertPreProcessor = new PojoUpsertPreProcessor();
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<EntityHolder<Object>> process(Object entity, EntityContext context) {
        Validate.notNull(entity, "entity must not be null");
        Object ret;
        if(entity instanceof TokenBuffer) {
            ret = tokenBufferUpsertPreProcessor.process((TokenBuffer) entity, context);
        }else if (entity instanceof RawJson){
            ret = rawJsonUpsertPreProcessor.process((RawJson) entity, context);
        } else if(entity instanceof Map) {
            ret = mapUpsertPreProcessor.process((Map<Object, Object>) entity, context);
        } else {
            ret = pojoUpsertPreProcessor.process(entity, context);
        }
        return (CompletableFuture<EntityHolder<Object>>) ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<List<EntityHolder<Object>>> processArray(Object entities, EntityContext context) {
        Validate.notNull(entities, "entities must not be null");

        Object ret;
        if(entities instanceof TokenBuffer) {
            ret = tokenBufferUpsertPreProcessor.processArray((TokenBuffer) entities, context);
        } else if (entities instanceof RawJson) {
            ret = rawJsonUpsertPreProcessor.processArray((RawJson) entities, context);
        } else if(entities instanceof List<?> list) {
            if(!list.isEmpty()){
                Object first = list.getFirst();
                if(first instanceof Map){
                    ret = mapUpsertPreProcessor.processArray((List<Map<Object, Object>>) entities, context);
                }else{
                    ret = pojoUpsertPreProcessor.processArray((List<Object>) entities, context);
                }
            }else{
                ret = CompletableFuture.completedFuture(new ArrayList<>());
            }
        }else {
            throw new IllegalArgumentException("Unsupported type: " + entities.getClass().getName());
        }
        return (CompletableFuture<List<EntityHolder<Object>>>) ret;
    }
}
