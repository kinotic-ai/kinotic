package org.kinotic.structures.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;
import org.kinotic.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪on 6/8/23.
 */
@Getter
@Setter
@Accessors(chain = true)
public final class NestedDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "Nested";

    public NestedDecorator() {
        this.targets = List.of(DecoratorTarget.FIELD);
    }

}
