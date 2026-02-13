package org.kinotic.structures.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;
import org.kinotic.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
public final class ReadOnlyDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "ReadOnly";

    public ReadOnlyDecorator(){
        this.targets = List.of(DecoratorTarget.FIELD);
    }

}
