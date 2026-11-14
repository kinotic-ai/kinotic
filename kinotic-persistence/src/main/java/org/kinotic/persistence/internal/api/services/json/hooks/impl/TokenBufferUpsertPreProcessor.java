package org.kinotic.persistence.internal.api.services.json.hooks.impl;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.config.DomainPersistenceProperties;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.internal.api.services.json.hooks.UpsertFieldTransformer;
import org.kinotic.persistence.internal.api.services.EntityHolder;
import org.kinotic.persistence.internal.api.services.json.JsonStreamProcessor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Prepares entities of one {@link EntityDescriptor} for Elasticsearch before they are saved or updated, as
 * {@link EntityUpsertStrategy} describes.
 * Created by Navíd Mitchell 🤪 on 2/10/25.
 */
public class TokenBufferUpsertPreProcessor {

    private final JsonStreamProcessor<TokenBuffer, EntityContext, EntityHolder<RawJson>> processor;

    /**
     * @param jsonMapper        reads each transformed field's value from the middle of the input, so it must have
     *                          {@link DeserializationFeature#FAIL_ON_TRAILING_TOKENS} disabled
     * @param fieldTransformers the transformer for each decorated json path of the entity
     */
    public TokenBufferUpsertPreProcessor(DomainPersistenceProperties domainPersistenceProperties,
                                         JsonMapper jsonMapper,
                                         EntityDescriptor entityDescriptor,
                                         Map<String, UpsertFieldTransformer> fieldTransformers) {
        this.processor = JsonStreamProcessor.create(new EntityUpsertStrategy(domainPersistenceProperties,
                                                                             jsonMapper,
                                                                             entityDescriptor,
                                                                             fieldTransformers));
    }

    /**
     * Processes a single entity.
     * @param entity a json object
     * @param context the context of the save or update
     * @return {@link Future} emitting the entity ready to store, or failing if it is invalid
     */
    @WithSpan
    public Future<EntityHolder<RawJson>> process(TokenBuffer entity, EntityContext context) {
        Validate.notNull(entity, "entity must not be null");
        return complete(() -> processor.process(entity, context));
    }

    /**
     * Processes a json array of entities; one invalid entity fails them all.
     * @param entities a json array of objects
     * @param context the context of the bulk save or update
     * @return {@link Future} emitting the entities ready to store, in input order
     */
    @WithSpan
    public Future<List<EntityHolder<RawJson>>> processArray(TokenBuffer entities, EntityContext context) {
        Validate.notNull(entities, "entities must not be null");
        return complete(() -> processor.processArray(entities, context));
    }

    private static <R> Future<R> complete(Supplier<R> processing) {
        Future<R> ret;
        try {
            ret = Future.succeededFuture(processing.get());
        } catch (Exception e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }
}
