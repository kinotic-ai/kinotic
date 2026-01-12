package org.mindignited.structures.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.mindignited.continuum.idl.api.schema.decorators.C3Decorator;
import org.mindignited.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪on 6/16/23.
 */
@Getter
@Setter
@Accessors(chain = true)
public final class DiscriminatorDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "Discriminator";

    private String propertyName = null;

    public DiscriminatorDecorator() {
        this.targets = List.of(DecoratorTarget.FIELD);
    }
}
