package org.mindignited.structures.internal.api.hooks.impl;

import org.mindignited.structures.api.domain.EntityContext;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.idl.decorators.VersionDecorator;
import org.mindignited.structures.internal.api.hooks.UpsertFieldPreProcessor;
import org.mindignited.structures.internal.api.hooks.UpsertPreProcessor;
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
    public String process(Structure structure, String fieldName, VersionDecorator decorator, String fieldValue, EntityContext context) {
        return fieldValue;
    }
}
