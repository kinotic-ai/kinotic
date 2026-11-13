package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.domain.api.model.persistence.idl.decorators.TimeReferenceDecorator;
import org.kinotic.persistence.internal.api.hooks.EntityUpsertState;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldTransformer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import java.util.Date;

/**
 * Writes a {@link TimeReferenceDecorator} field's value a second time under {@code @timestamp}, the field
 * Elasticsearch reads an item's time from.
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Component
public class TimeReferenceUpsertFieldTransformer implements UpsertFieldTransformer {

    @Override
    public Class<TimeReferenceDecorator> implementsDecorator() {
        return TimeReferenceDecorator.class;
    }

    @Override
    public void transform(JsonParser parser, JsonGenerator generator, EntityUpsertState state) {
        String fieldName = parser.currentName();
        parser.nextToken();
        Date time = state.readValue(parser, Date.class);

        writeField(generator, fieldName, time);

        if(state.isRootField()){
            writeField(generator, "@timestamp", time);
        }
    }
}
