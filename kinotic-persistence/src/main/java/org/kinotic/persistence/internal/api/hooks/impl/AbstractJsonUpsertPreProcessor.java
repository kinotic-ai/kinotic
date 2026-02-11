package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.databind.json.JsonMapper;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.api.domain.EntityContext;
import org.kinotic.persistence.api.domain.RawJson;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.idl.decorators.*;
import org.kinotic.persistence.internal.api.hooks.DecoratorLogic;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldPreProcessor;
import org.kinotic.persistence.internal.api.hooks.UpsertPreProcessor;
import org.kinotic.persistence.internal.api.services.EntityHolder;
import org.kinotic.persistence.internal.api.services.json.JsonStreamProcessor;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * This class was created to make the extraction of needed data as performant as possible
 * since elasticsearch already expects json there is not a need to convert to a java object.
 * For this reason the code below is a single loop limiting allocations as much as possible.
 * NOTE: this will all be removed in favor of the new {@link JsonStreamProcessor} I am working on.
 * Created by Navíd Mitchell 🤪 on 5/5/23.
 */
public abstract class AbstractJsonUpsertPreProcessor<T> implements UpsertPreProcessor<T, T, RawJson> {

    /** Mapper with FAIL_ON_TRAILING_TOKENS disabled for stream reads (Jackson 3; not needed in Jackson 2). */
    protected final JsonMapper jsonMapper;
    protected final Structure structure;
    protected final StructuresProperties structuresProperties;
    // Map of json path to decorator logic
    private final Map<String, DecoratorLogic> fieldPreProcessors;

    public AbstractJsonUpsertPreProcessor(StructuresProperties structuresProperties,
                                          JsonMapper jsonMapper,
                                          Structure structure,
                                          Map<String, DecoratorLogic> fieldPreProcessors) {
        this.structuresProperties = structuresProperties;
        // Jackson 3 fails on trailing tokens by default; we stream-parse and readValue() one field at a time,
        // leaving the parser on the next token (e.g. next property). Disable so partial reads succeed (Jackson 2 allowed this).
        this.jsonMapper = jsonMapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        this.structure = structure;
        this.fieldPreProcessors = fieldPreProcessors;
    }

    @Override
    public CompletableFuture<EntityHolder<RawJson>> process(T entity, EntityContext context) {
        return doProcess(entity, context, false).thenApply(list -> {
            if(list.size() != 1){
                throw new IllegalStateException("Expected exactly one entity to be returned, got " + list.size());
            }
            return list.getFirst();
        });
    }

    @Override
    public CompletableFuture<List<EntityHolder<RawJson>>> processArray(T entities, EntityContext context) {
        return doProcess(entities, context, true);
    }

    protected abstract JsonParser createParser(T input);

    /**
     * Will process the given {@link RawJson} and return a new {@link RawJson} with the appropriate decorators applied
     * Handles both single and array of entities
     * @param json the json to process
     * @param context the context of the entity
     * @return a new {@link RawJson} with the appropriate decorators applied
     */
    private CompletableFuture<List<EntityHolder<RawJson>>> doProcess(T json, EntityContext context, boolean processArray){
        Deque<String> jsonPathStack = new ArrayDeque<>();
        List<EntityHolder<RawJson>> ret = new ArrayList<>();
        List<String> tenantsSelected = new ArrayList<>();

        int objectDepth = 0;
        int arrayDepth = 0;

        try(JsonParser jsonParser = createParser(json)) {
            String currentId = null;
            String currentTenantId = null;
            String currentVersion = null;
            ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            JsonGenerator jsonGenerator = jsonMapper.createGenerator(byteArrayBuilder, JsonEncoding.UTF8);

            while (jsonParser.nextToken() != null) {

                JsonToken token = jsonParser.currentToken();

                if (token == JsonToken.PROPERTY_NAME) {

                    String fieldName = jsonParser.currentName();

                    // if the stack depth and the object depth are the same we are at a new field in the same object so pop the stack
                    if(jsonPathStack.size() == objectDepth){
                        jsonPathStack.removeFirst();
                    }

                    String currentJsonPath = !jsonPathStack.isEmpty() ? jsonPathStack.peekFirst() + "." + fieldName : fieldName;
                    jsonPathStack.addFirst(currentJsonPath);

                    DecoratorLogic preProcessorLogic = fieldPreProcessors.get(currentJsonPath);

                    // Apply preProcessor if any exist
                    if(preProcessorLogic != null){

                        jsonParser.nextToken(); // move to value token

                        C3Decorator decorator = preProcessorLogic.getDecorator();
                        UpsertFieldPreProcessor<C3Decorator, Object, Object> preProcessor = preProcessorLogic.getProcessor();
                        Object input = jsonMapper.readValue(jsonParser, preProcessor.supportsFieldType());
                        Object value = preProcessor.process(structure, fieldName, decorator, input, context);

                        // We exclude the version field from the data to be persisted
                        if(!(decorator instanceof VersionDecorator)) {
                            if (value != null) {
                                jsonGenerator.writePOJOProperty(fieldName, value);
                            } else {
                                jsonGenerator.writeNullProperty(fieldName);
                            }
                        }

                        // I feel like the logic below is getting kinda dumb since the knowledge of certain decorators is hardcoded
                        // and doesn't really seem to use the basic abstraction well
                        // The id value comes from a top level object that is annotated wih @Id or @AutoGeneratedId
                        if(objectDepth == 1 && (decorator instanceof IdDecorator
                                || decorator instanceof AutoGeneratedIdDecorator)){

                            if(value == null || ((String)value).isBlank()){
                                throw new IllegalArgumentException("Id field cannot be null or blank");
                            }

                            if(currentId != null){ // should never happen, because the structure is validated when published
                                throw new IllegalArgumentException("Found multiple id fields in entity");
                            }

                            // if this is the id we add the special _id field for elasticsearch to use
                            currentId = (String) value;

                        }else if(objectDepth == 1 && decorator instanceof VersionDecorator) {

                            if (currentVersion != null) { // should never happen, because the structure is validated when published
                                throw new IllegalArgumentException("Found multiple Version fields in entity");
                            }

                            currentVersion = (String) value;
                        }else if(objectDepth == 1 && decorator instanceof TenantIdDecorator){

                            if(currentTenantId != null){ // should never happen, because the structure is validated when published
                                throw new IllegalArgumentException("Found multiple id fields in entity");
                            }
                            // field exists but is null so we can throw early
                            if(value == null){
                                throw new IllegalArgumentException("Tenant Id field cannot be null");
                            }

                            currentTenantId = (String) value;
                        }else if(objectDepth == 1 && decorator instanceof TimeReferenceDecorator){

                            // Elasticsearch requires a @timestamp field to contain the time data so we just duplicate the value
                            if(value != null){
                                jsonGenerator.writePOJOProperty("@timestamp", value);
                            }else{
                                jsonGenerator.writeNullProperty("@timestamp");
                            }
                        }
                    }else{
                        // Check if this is the tenant id if MultiTenancyType.SHARED is enabled
                        if(structure.getMultiTenancyType() == MultiTenancyType.SHARED
                                && !structure.isMultiTenantSelectionEnabled() // just in case there is a field with the same name as the configured prop
                                && currentJsonPath.equals(structuresProperties.getTenantIdFieldName())){

                            // since the tenant id field is already present check its value to make sure it is null
                            // or matches the logged in tenant
                            jsonParser.nextToken(); // move to value token
                            currentTenantId = jsonMapper.readValue(jsonParser, String.class);
                            if(currentTenantId != null && !currentTenantId.equals(context.getParticipant().getTenantId())){
                                throw new IllegalArgumentException("Tenant Id invalid for logged in participant");
                            }

                            jsonGenerator.writeStringProperty(fieldName, currentTenantId);

                        }else{
                            jsonGenerator.copyCurrentEvent(jsonParser);
                        }
                    }

                }else{

                    // End of root level object
                    if(token == JsonToken.END_OBJECT && objectDepth == 1){

                        if(currentId == null){
                            throw new IllegalArgumentException("Could not find id for Entity");
                        }

                        // If this is enabled a tenant id should always be present in the data
                        if(structure.isMultiTenantSelectionEnabled() && currentTenantId == null){

                            throw new IllegalArgumentException("Could not find TenantId for Entity");

                        } else if (structure.isMultiTenantSelectionEnabled() && currentTenantId != null) {
                            tenantsSelected.add(currentTenantId);
                        }

                        // If this is a multi tenant structure and multi tenant selection is not enabled, add the tenant if necessary
                        if(structure.getMultiTenancyType() == MultiTenancyType.SHARED
                                && currentTenantId == null){
                            currentTenantId = context.getParticipant().getTenantId();
                            jsonGenerator.writeStringProperty(structuresProperties.getTenantIdFieldName(), currentTenantId);
                        }

                        // This is the end of the object, so we store the object
                        jsonGenerator.writeEndObject();
                        jsonGenerator.flush();
                        ret.add(new EntityHolder<>(new RawJson(byteArrayBuilder.toByteArray()),
                                                   currentId,
                                                   structure.getMultiTenancyType(),
                                                   currentTenantId,
                                                   currentVersion
                        ));
                        byteArrayBuilder.reset();
                        currentId = null;
                        currentTenantId = null;
                        currentVersion = null;
                    }else{
                        if(!shouldSkipToken(token, jsonParser.currentValue(), arrayDepth, processArray)){
                            jsonGenerator.copyCurrentEvent(jsonParser);
                        }
                    }

                    if(token == JsonToken.START_OBJECT){
                        objectDepth++;
                    }else if(token == JsonToken.END_OBJECT){

                        // if the stack depth and the object depth are the same, the last field is done so pop the stack
                        if(jsonPathStack.size() == objectDepth){
                            jsonPathStack.removeFirst();
                        }

                        objectDepth--;
                    }

                    if(token == JsonToken.START_ARRAY){
                        arrayDepth++;
                    }else if(token == JsonToken.END_ARRAY){
                        arrayDepth--;
                    }
                }
            }

            // We always blow away tenant selection on save/update since the only tenants that mater are the ones in the data
            // This is a sanity check, in case somehow it was already provided. We want to make sure auth services see the correct list.
            if(structure.isMultiTenantSelectionEnabled()){
                context.setTenantSelection(tenantsSelected);
            }

            return CompletableFuture.completedFuture(ret);

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private boolean shouldSkipToken(JsonToken token, Object currentValue, int arrayDepth, boolean processArray){
        boolean ret = false;
        if(processArray) {
            if (token == JsonToken.START_ARRAY && arrayDepth == 0) {
                ret = true;
            } else if (token == JsonToken.END_ARRAY && arrayDepth == 1) {
                ret = true;
            } else if (arrayDepth == 1 && (  ",".equals(currentValue) || " ".equals(currentValue) )) {
                ret = true;
            }
        }
        return ret;
    }


}
