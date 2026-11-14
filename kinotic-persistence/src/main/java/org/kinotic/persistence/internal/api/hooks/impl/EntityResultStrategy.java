package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.domain.api.model.RawJson;
import org.kinotic.persistence.internal.api.hooks.EntityResultState;
import org.kinotic.persistence.internal.api.services.ElasticVersion;
import org.kinotic.persistence.internal.api.services.json.JsonStreamProcessorStrategy;
import org.kinotic.persistence.internal.api.services.json.JsonTransformer;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Writes an entity's stored document out for its caller. For an entity with a version field the version the entity
 * is at is written under that field, as {@code primaryTerm:seqNo}; the rest of the document is written as stored.
 *
 * @param <O> the type of the output the document is written to
 * @param <E> the type the written document is returned as
 */
public class EntityResultStrategy<O, E> implements JsonStreamProcessorStrategy<RawJson, ElasticVersion, EntityResultState<O>, E> {

    private final JsonMapper jsonMapper;
    private final String versionFieldName;
    private final Function<JsonMapper, O> outputFactory;
    private final BiFunction<JsonMapper, O, JsonGenerator> generatorFactory;
    private final Function<O, E> resultFactory;
    private final Map<String, JsonTransformer<EntityResultState<O>>> transformers;

    private EntityResultStrategy(JsonMapper jsonMapper,
                                 String versionFieldName,
                                 Function<JsonMapper, O> outputFactory,
                                 BiFunction<JsonMapper, O, JsonGenerator> generatorFactory,
                                 Function<O, E> resultFactory) {
        this.jsonMapper = jsonMapper;
        this.versionFieldName = versionFieldName;
        this.outputFactory = outputFactory;
        this.generatorFactory = generatorFactory;
        this.resultFactory = resultFactory;
        this.transformers = versionFieldName != null ? Map.of(versionFieldName, EntityResultStrategy::dropStoredVersion)
                                                     : Map.of();
    }

    /**
     * @param versionFieldName the entity's version field, or null for an entity without one
     * @return a strategy returning the document as {@link RawJson}
     */
    public static EntityResultStrategy<ByteArrayBuilder, RawJson> toRawJson(JsonMapper jsonMapper, String versionFieldName) {
        return new EntityResultStrategy<>(jsonMapper,
                                          versionFieldName,
                                          mapper -> new ByteArrayBuilder(),
                                          (mapper, output) -> mapper.createGenerator(output, JsonEncoding.UTF8),
                                          output -> new RawJson(output.toByteArray()));
    }

    /**
     * @param versionFieldName the entity's version field, or null for an entity without one
     * @return a strategy returning the document as a {@link TokenBuffer}
     */
    public static EntityResultStrategy<TokenBuffer, TokenBuffer> toTokenBuffer(JsonMapper jsonMapper, String versionFieldName) {
        return new EntityResultStrategy<>(jsonMapper,
                                          versionFieldName,
                                          mapper -> new TokenBuffer(mapper._serializationContext(), false),
                                          (mapper, output) -> output,
                                          Function.identity());
    }

    @Override
    public Map<String, JsonTransformer<EntityResultState<O>>> getTransformers() {
        return transformers;
    }

    @Override
    public EntityResultState<O> createState(ElasticVersion version) {
        return new EntityResultState<>(version, outputFactory.apply(jsonMapper));
    }

    @Override
    public JsonParser createParser(RawJson input) {
        return jsonMapper.createParser(input.data());
    }

    @Override
    public JsonGenerator createGenerator(EntityResultState<O> state) {
        return generatorFactory.apply(jsonMapper, state.getOutput());
    }

    @Override
    public void onRootObjectEnd(JsonGenerator generator, EntityResultState<O> state) {
        if(versionFieldName != null){
            ElasticVersion version = state.getVersion();
            generator.writeStringProperty(versionFieldName, version.primaryTerm() + ":" + version.seqNo());
        }
    }

    @Override
    public E emit(EntityResultState<O> state) {
        return resultFactory.apply(state.getOutput());
    }

    @Override
    public void onComplete(EntityResultState<O> state) {
    }

    // A document written outside the entity service can hold a value under the version field; the version the
    // entity is at is written in its place
    private static <O> void dropStoredVersion(JsonParser parser, JsonGenerator generator, EntityResultState<O> state) {
        parser.nextToken();
        parser.skipChildren();
    }
}
