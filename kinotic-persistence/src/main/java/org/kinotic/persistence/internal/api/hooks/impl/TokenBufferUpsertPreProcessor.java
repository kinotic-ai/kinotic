package org.kinotic.persistence.internal.api.hooks.impl;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.api.domain.EntityContext;
import org.kinotic.core.api.domain.RawJson;
import org.kinotic.persistence.api.domain.Structure;
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


    public TokenBufferUpsertPreProcessor(StructuresProperties structuresProperties,
                                         JsonMapper jsonMapper,
                                         Structure structure,
                                         Map<String, DecoratorLogic> fieldPreProcessors) {
        super(structuresProperties, jsonMapper, structure, fieldPreProcessors);
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
