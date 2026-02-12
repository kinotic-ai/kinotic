package org.kinotic.persistence.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * By default, each subfield in an object is mapped and indexed separately.
 * <p>
 * The flattened type provides an alternative approach, where the entire object is mapped as a single field.
 * Given an object, the flattened mapping will parse out its leaf values and index them into one field as keywords.
 * The object’s contents can then be searched through simple queries and aggregations.
 * <p>
 * Created by Navíd Mitchell 🤪 on 5/26/23.
 */
@Getter
@Setter
@Accessors(chain = true)
public final class FlattenedDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "Flattened";

    /**
     * The maximum allowed depth of the flattened object field, in terms of nested inner objects.
     * If a flattened object field exceeds this limit, then an error will be thrown. Defaults to 20.
     */
    private int depthLimit = 20;

    /**
     * Determines if the field should be searchable. Accepts true (default) or false.
     */
    private boolean index = true;

    public FlattenedDecorator() {
        this.targets = List.of(DecoratorTarget.FIELD);
    }

}
