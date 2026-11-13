package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.domain.api.model.persistence.idl.decorators.VersionDecorator;
import org.kinotic.persistence.internal.api.hooks.EntityUpsertState;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldTransformer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

/**
 * Records a {@link VersionDecorator} field as the version the write is checked against. The field is left out of
 * the stored document.
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Component
public class VersionUpsertFieldTransformer implements UpsertFieldTransformer {

    @Override
    public Class<VersionDecorator> implementsDecorator() {
        return VersionDecorator.class;
    }

    @Override
    public void transform(JsonParser parser, JsonGenerator generator, EntityUpsertState state) {
        String version = state.readFieldValue(parser, String.class);

        if(state.isRootField()){
            // reached by a payload that repeats the version field, since a published definition holds a single one
            if(state.getVersion() != null){
                throw new IllegalArgumentException("Found multiple Version fields in entity");
            }
            state.setVersion(version);
        }
    }
}
