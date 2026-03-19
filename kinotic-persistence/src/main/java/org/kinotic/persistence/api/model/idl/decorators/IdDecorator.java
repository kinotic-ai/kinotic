package org.kinotic.persistence.api.model.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
public final class IdDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "Id";

    public IdDecorator(){
        this.targets = List.of(DecoratorTarget.FIELD);
    }
}
