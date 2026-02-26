package org.kinotic.persistence.api.model.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Used to denote a field to be used as a version to support optimistic locking
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
public final class VersionDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "VersionDecorator";

    public VersionDecorator(){
        this.targets = List.of(DecoratorTarget.FIELD);
    }
}
