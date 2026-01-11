package org.mindignited.structures.internal.api.hooks.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.mindignited.structures.api.config.StructuresProperties;
import org.mindignited.structures.api.domain.EntityContext;
import org.mindignited.structures.api.domain.RawJson;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.internal.api.hooks.DecoratorLogic;
import org.mindignited.structures.internal.api.services.EntityHolder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 2/10/25.
 */
public class TokenBufferUpsertPreProcessor extends AbstractJsonUpsertPreProcessor<TokenBuffer> {


    public TokenBufferUpsertPreProcessor(StructuresProperties structuresProperties,
                                         ObjectMapper objectMapper,
                                         Structure structure,
                                         Map<String, DecoratorLogic> fieldPreProcessors) {
        super(structuresProperties, objectMapper, structure, fieldPreProcessors);
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
