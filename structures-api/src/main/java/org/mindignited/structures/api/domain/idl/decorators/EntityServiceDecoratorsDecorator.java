package org.mindignited.structures.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;
import org.kinotic.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class EntityServiceDecoratorsDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "EntityServiceDecorators";

    @EqualsAndHashCode.Exclude
    private EntityServiceDecoratorsConfig config;

    public EntityServiceDecoratorsDecorator() {
        this.targets = List.of(DecoratorTarget.TYPE);
    }
}
