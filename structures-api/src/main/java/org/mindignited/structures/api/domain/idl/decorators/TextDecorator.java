package org.mindignited.structures.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mindignited.continuum.idl.api.schema.StringC3Type;
import org.mindignited.continuum.idl.api.schema.decorators.C3Decorator;
import org.mindignited.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * {@link TextDecorator} defines a {@link StringC3Type} as a text field
 * Created by Navíd Mitchell 🤪 on 5/14/23.
 */
public final class TextDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "Text";

    public TextDecorator() {
        this.targets = List.of(DecoratorTarget.FIELD);
    }
}
