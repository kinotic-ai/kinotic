package org.kinotic.persistence.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;


@Getter
@Setter
@Accessors(chain = true)
public class PolicyDecorator extends EntityServiceDecorator {

    @JsonIgnore
    public static final String type = "PolicyDecorator";

    private List<List<String>> policies = List.of();

    public PolicyDecorator() {
        this.targets = List.of(DecoratorTarget.TYPE, DecoratorTarget.FIELD, DecoratorTarget.FUNCTION);
    }
}
