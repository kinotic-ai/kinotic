package org.mindignited.structures.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mindignited.continuum.idl.api.schema.decorators.C3Decorator;
import org.mindignited.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * {@link TimeReferenceDecorator} is used for data streams to signify the field that should be used as the time reference.
 * Created by Navíd Mitchell 🤪 on 5/14/23.
 */
public final class TimeReferenceDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "TimeReferenceDecorator";

    public TimeReferenceDecorator() {
        this.targets = List.of(DecoratorTarget.FIELD);
    }
}
