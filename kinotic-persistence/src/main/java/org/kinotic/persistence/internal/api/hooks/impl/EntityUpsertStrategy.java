package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.domain.api.config.DomainPersistenceProperties;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.internal.api.hooks.EntityUpsertState;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldTransformer;
import org.kinotic.persistence.internal.api.services.EntityHolder;
import org.kinotic.persistence.internal.api.services.json.JsonStreamProcessorStrategy;
import org.kinotic.persistence.internal.api.services.json.JsonTransformer;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prepares the entities of one {@link EntityDescriptor} for Elasticsearch as they are saved or updated: each root
 * object becomes the document to store, held in an {@link EntityHolder} with the id, tenant and version the write
 * is addressed and checked by. A root object without an id is rejected. On a {@link MultiTenancyType#SHARED}
 * entity the tenant is taken from the participant, or from the data when the entity has a tenant id field, and a
 * participant naming tenants through the data has its tenant selection replaced by the tenants the data names.
 */
public class EntityUpsertStrategy implements JsonStreamProcessorStrategy<TokenBuffer, EntityContext, EntityUpsertState, EntityHolder<RawJson>> {

    private final DomainPersistenceProperties domainPersistenceProperties;
    private final JsonMapper jsonMapper;
    private final EntityDescriptor entityDescriptor;
    private final Map<String, JsonTransformer<EntityUpsertState>> transformers;

    /**
     * @param fieldTransformers the transformer for each decorated json path of the entity
     */
    public EntityUpsertStrategy(DomainPersistenceProperties domainPersistenceProperties,
                                JsonMapper jsonMapper,
                                EntityDescriptor entityDescriptor,
                                Map<String, UpsertFieldTransformer> fieldTransformers) {
        this.domainPersistenceProperties = domainPersistenceProperties;
        // Jackson 3 fails on trailing tokens by default; a transformer reads one field's value and leaves the
        // parser on the rest of the input, so the check must be off for those partial reads
        this.jsonMapper = jsonMapper.rebuild()
                                    .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                    .build();
        this.entityDescriptor = entityDescriptor;
        this.transformers = new LinkedHashMap<>(fieldTransformers);

        // a decorated field of the same name keeps its own transformer
        if(entityDescriptor.multiTenancyType() == MultiTenancyType.SHARED
                && !entityDescriptor.isMultiTenantSelectionEnabled()){
            this.transformers.putIfAbsent(domainPersistenceProperties.getTenantIdFieldName(), this::transformTenantField);
        }
    }

    @Override
    public Map<String, JsonTransformer<EntityUpsertState>> getTransformers() {
        return transformers;
    }

    @Override
    public EntityUpsertState createState(EntityContext context) {
        return new EntityUpsertState(context, jsonMapper);
    }

    @Override
    public JsonParser createParser(TokenBuffer input) {
        return input.asParser();
    }

    @Override
    public JsonGenerator createGenerator(EntityUpsertState state) {
        return jsonMapper.createGenerator(state.getBuffer(), JsonEncoding.UTF8);
    }

    @Override
    public void onRootObjectEnd(JsonGenerator generator, EntityUpsertState state) {
        if(state.getId() == null){
            throw new IllegalArgumentException("Could not find id for Entity");
        }

        if(entityDescriptor.isMultiTenantSelectionEnabled()){
            if(state.getTenantId() == null){
                throw new IllegalArgumentException("Could not find TenantId for Entity");
            }
            state.getTenantsSelected().add(state.getTenantId());
        }

        if(entityDescriptor.multiTenancyType() == MultiTenancyType.SHARED && state.getTenantId() == null){
            state.setTenantId(state.getContext().requireTenantId());
            generator.writeStringProperty(domainPersistenceProperties.getTenantIdFieldName(), state.getTenantId());
        }
    }

    @Override
    public EntityHolder<RawJson> emit(EntityUpsertState state) {
        EntityHolder<RawJson> ret = new EntityHolder<>(new RawJson(state.getBuffer().toByteArray()),
                                                       state.getId(),
                                                       entityDescriptor.multiTenancyType(),
                                                       state.getTenantId(),
                                                       state.getVersion());
        state.resetRootObject();
        return ret;
    }

    @Override
    public void onComplete(EntityUpsertState state) {
        // the tenants that matter on a save or update are the ones the data names; replacing the selection
        // means authorization sees exactly those, whatever the caller set
        if(entityDescriptor.isMultiTenantSelectionEnabled()){
            state.getContext().setTenantSelection(state.getTenantsSelected());
        }
    }

    // The tenant field of a SHARED entity without a tenant id field may be given only as the participant's tenant
    private void transformTenantField(JsonParser parser, JsonGenerator generator, EntityUpsertState state) {
        String fieldName = parser.currentName();
        parser.nextToken();
        String tenantId = state.readValue(parser, String.class);
        if(tenantId != null && !tenantId.equals(state.getContext().requireTenantId())){
            throw new IllegalArgumentException("Tenant Id invalid for logged in participant");
        }
        state.setTenantId(tenantId);
        generator.writeStringProperty(fieldName, tenantId);
    }
}
