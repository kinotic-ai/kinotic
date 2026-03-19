package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.idl.decorators.IdDecorator;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldPreProcessor;
import org.kinotic.persistence.api.model.EntityContext;
import org.springframework.stereotype.Component;

/**
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Component
public class IdUpsertFieldPreProcessor implements UpsertFieldPreProcessor<IdDecorator, String, String> {

    @Override
    public Class<IdDecorator> implementsDecorator() {
        return IdDecorator.class;
    }

    @Override
    public Class<String> supportsFieldType() {
        return String.class;
    }

    @Override
    public String process(EntityDefinition entityDefinition, String fieldName, IdDecorator decorator, String fieldValue, EntityContext context) {
        if(fieldValue == null || fieldValue.isBlank()){
            throw new IllegalArgumentException("Id field cannot be null or blank");
        }
        return fieldValue;
    }
}
