package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.idl.decorators.VersionDecorator;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldPreProcessor;
import org.kinotic.persistence.internal.api.hooks.UpsertPreProcessor;
import org.springframework.stereotype.Component;

/**
 * This pretty much does nothing but the other logic in the {@link UpsertPreProcessor} already work with concept for the time being it will stay here.
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Component
public class VersionUpsertFieldPreProcessor implements UpsertFieldPreProcessor<VersionDecorator, String, String> {

    @Override
    public Class<VersionDecorator> implementsDecorator() {
        return VersionDecorator.class;
    }

    @Override
    public Class<String> supportsFieldType() {
        return String.class;
    }

    @Override
    public String process(EntityDefinition entityDefinition, String fieldName, VersionDecorator decorator, String fieldValue, EntityContext context) {
        return fieldValue;
    }
}
