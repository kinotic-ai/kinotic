package org.kinotic.persistence.api.model.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪on 6/16/23.
 */
@Getter
@Setter
@Accessors(chain = true)
public final class EntityDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "Entity";

    private MultiTenancyType multiTenancyType = MultiTenancyType.NONE;

    private EntityType entityType = EntityType.TABLE;

    public EntityDecorator() {
        this.targets = List.of(DecoratorTarget.TYPE);
    }
}
