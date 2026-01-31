package org.mindignited.structures.internal.api.hooks.impl;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.mindignited.structures.api.config.StructuresProperties;
import org.mindignited.structures.api.domain.EntityContext;
import org.mindignited.structures.api.domain.RawJson;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.internal.api.hooks.DecoratorLogic;
import org.mindignited.structures.internal.api.services.EntityHolder;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 2/10/25.
 */
public class RawJsonUpsertPreProcessor extends AbstractJsonUpsertPreProcessor<RawJson> {


    public RawJsonUpsertPreProcessor(StructuresProperties structuresProperties,
                                     JsonMapper jsonMapper,
                                     Structure structure,
                                     Map<String, DecoratorLogic> fieldPreProcessors) {
        super(structuresProperties, jsonMapper, structure, fieldPreProcessors);
    }

    @Override
    protected JsonParser createParser(RawJson input) {
        byte[] bytes = input.data();
        return jsonMapper.createParser(bytes);
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
