package org.kinotic.persistence.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class RoleDecorator extends EntityServiceDecorator {

    @JsonIgnore
    public static final String type = "RoleDecorator";

    private List<String> roles = List.of();

    public RoleDecorator() {
        this.targets = List.of(DecoratorTarget.TYPE, DecoratorTarget.FUNCTION);
    }
}
