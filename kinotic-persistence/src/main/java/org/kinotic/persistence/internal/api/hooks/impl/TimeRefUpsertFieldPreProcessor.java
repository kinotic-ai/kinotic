package org.kinotic.persistence.internal.api.hooks.impl;

import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.domain.idl.decorators.TimeReferenceDecorator;
import org.kinotic.persistence.internal.api.hooks.UpsertFieldPreProcessor;
import org.kinotic.persistence.internal.api.hooks.UpsertPreProcessor;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * This pretty much does nothing but the other logic in the {@link UpsertPreProcessor} already work with concept for the time being it will stay here.
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Component
public class TimeRefUpsertFieldPreProcessor implements UpsertFieldPreProcessor<TimeReferenceDecorator, Date, Date> {

    @Override
    public Class<TimeReferenceDecorator> implementsDecorator() {
        return TimeReferenceDecorator.class;
    }

    @Override
    public Class<Date> supportsFieldType() {
        return Date.class;
    }

    @Override
    public Date process(Structure structure, String fieldName, TimeReferenceDecorator decorator, Date fieldValue, EntityContext context) {
        return fieldValue;
    }
}
