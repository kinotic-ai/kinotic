package org.kinotic.persistence.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;
import org.kinotic.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Specifies that a field should not be indexed in Elasticsearch.
 * Created By Navíd Mitchell 🤪on 3/18/25
 */
public class NotIndexedDecorator extends C3Decorator {


    @JsonIgnore
    public static final String type = "NotIndexedDecorator";

    public NotIndexedDecorator() {
        this.targets = List.of(DecoratorTarget.FIELD);
    }

}
