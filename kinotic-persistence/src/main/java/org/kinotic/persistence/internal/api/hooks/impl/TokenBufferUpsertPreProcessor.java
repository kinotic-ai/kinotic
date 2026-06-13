package org.kinotic.persistence.internal.api.hooks.impl;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.internal.api.hooks.DecoratorLogic;
import org.kinotic.persistence.internal.api.services.EntityHolder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;
import tools.jackson.core.JsonParser;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 2/10/25.
 */
public class TokenBufferUpsertPreProcessor extends AbstractJsonUpsertPreProcessor<TokenBuffer> {


    public TokenBufferUpsertPreProcessor(PersistenceProperties persistenceProperties,
                                         JsonMapper jsonMapper,
                                         EntityDefinition entityDefinition,
                                         Map<String, DecoratorLogic> fieldPreProcessors) {
        super(persistenceProperties, jsonMapper, entityDefinition, fieldPreProcessors);
    }

    @Override
    protected JsonParser createParser(TokenBuffer input) {
        return input.asParser();
    }

    @WithSpan
    @Override
    public CompletableFuture<EntityHolder<RawJson>> process(TokenBuffer entity, EntityContext context) {
        return super.process(entity, context);
    }

    @WithSpan
    @Override
    public CompletableFuture<List<EntityHolder<RawJson>>> processArray(TokenBuffer entities, EntityContext context) {
        return super.processArray(entities, context);
    }
}
