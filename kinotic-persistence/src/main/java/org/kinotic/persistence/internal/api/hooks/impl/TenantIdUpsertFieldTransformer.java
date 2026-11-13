package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.domain.api.model.persistence.idl.decorators.TenantIdDecorator;
import org.kinotic.persistence.internal.api.hooks.EntityUpsertState;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldTransformer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

/**
 * Resolves the value of a {@link TenantIdDecorator} field on save. A participant that belongs to a tenant
 * owns the field: an unset value is filled with that tenant and a different value is rejected. A participant
 * without a tenant, such as a service connected at organization scope, names the tenant through the data.
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Component
public class TenantIdUpsertFieldTransformer implements UpsertFieldTransformer {

    @Override
    public Class<TenantIdDecorator> implementsDecorator() {
        return TenantIdDecorator.class;
    }

    @Override
    public void transform(JsonParser parser, JsonGenerator generator, EntityUpsertState state) {
        String fieldName = parser.currentName();
        String tenantId = resolveTenantId(state.readFieldValue(parser, String.class), state.getContext().getTenantId());

        generator.writeStringProperty(fieldName, tenantId);

        if(state.isRootField()){
            // reached by a payload that repeats the tenant id field, since a published definition holds a single one
            if(state.getTenantId() != null){
                throw new IllegalArgumentException("Found multiple tenant id fields in entity");
            }
            if(tenantId == null){
                throw new IllegalArgumentException("Tenant Id field cannot be null");
            }
            state.setTenantId(tenantId);
        }
    }

    private String resolveTenantId(String fieldValue, String participantTenantId) {
        String ret;
        if(participantTenantId == null){
            ret = fieldValue;
        }else if(fieldValue == null || fieldValue.isBlank()){
            ret = participantTenantId;
        }else if(fieldValue.equals(participantTenantId)){
            ret = fieldValue;
        }else{
            throw new IllegalArgumentException("Tenant Id invalid for logged in participant");
        }
        return ret;
    }
}
