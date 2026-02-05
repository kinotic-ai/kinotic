package org.kinotic.structures.internal.api.hooks.impl;

import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.domain.idl.decorators.TenantIdDecorator;
import org.kinotic.structures.internal.api.hooks.UpsertFieldPreProcessor;
import org.kinotic.structures.internal.api.hooks.UpsertPreProcessor;
import org.springframework.stereotype.Component;

/**
 * This pretty much does nothing but the other logic in the {@link UpsertPreProcessor} already work with concept for the time being it will stay here.
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Component
public class TenantIdUpsertFieldPreProcessor implements UpsertFieldPreProcessor<TenantIdDecorator, String, String> {

    @Override
    public Class<TenantIdDecorator> implementsDecorator() {
        return TenantIdDecorator.class;
    }

    @Override
    public Class<String> supportsFieldType() {
        return String.class;
    }

    @Override
    public String process(Structure structure, String fieldName, TenantIdDecorator decorator, String fieldValue, EntityContext context) {
        return fieldValue;
    }
}
