package org.kinotic.structures.internal.api.hooks.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.kinotic.structures.api.config.StructuresProperties;
import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.structures.api.domain.RawJson;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.internal.api.hooks.DecoratorLogic;
import org.kinotic.structures.internal.api.services.EntityHolder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 2/10/25.
 */
public class RawJsonUpsertPreProcessor extends AbstractJsonUpsertPreProcessor<RawJson> {


    public RawJsonUpsertPreProcessor(StructuresProperties structuresProperties,
                                     ObjectMapper objectMapper,
                                     Structure structure,
                                     Map<String, DecoratorLogic> fieldPreProcessors) {
        super(structuresProperties, objectMapper, structure, fieldPreProcessors);
    }

    @Override
    protected JsonParser createParser(RawJson input) {
        try {
            byte[] bytes = input.data();
            JsonParser jsonParser = objectMapper.createNonBlockingByteArrayParser();
            ByteArrayFeeder feeder = (ByteArrayFeeder) jsonParser.getNonBlockingInputFeeder();
            feeder.feedInput(bytes, 0, bytes.length);
            feeder.endOfInput();
            return jsonParser;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @WithSpan
    @Override
    public CompletableFuture<EntityHolder<RawJson>> process(RawJson entity, EntityContext context) {
        return super.process(entity, context);
    }

    @WithSpan
    @Override
    public CompletableFuture<List<EntityHolder<RawJson>>> processArray(RawJson entities, EntityContext context) {
        return super.processArray(entities, context);
    }
}
