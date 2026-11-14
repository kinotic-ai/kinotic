package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.domain.api.model.persistence.idl.decorators.IdDecorator;
import org.kinotic.persistence.internal.api.hooks.EntityUpsertState;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldTransformer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

/**
 * Requires an {@link IdDecorator} field to hold a non-blank id, and records it as the entity's id.
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Component
public class IdUpsertFieldTransformer implements UpsertFieldTransformer {

    @Override
    public Class<IdDecorator> implementsDecorator() {
        return IdDecorator.class;
    }

    @Override
    public void transform(JsonParser parser, JsonGenerator generator, EntityUpsertState state) {
        String fieldName = parser.currentName();
        String id = state.readFieldValue(parser, String.class);
        if(id == null || id.isBlank()){
            throw new IllegalArgumentException("Id field cannot be null or blank");
        }

        generator.writeStringProperty(fieldName, id);

        if(state.isRootField()){
            state.recordId(id);
        }
    }
}
