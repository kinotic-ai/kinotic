package org.kinotic.persistence.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;
import org.kinotic.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Contains the statements for a named query
 * Created by Navíd Mitchell 🤪on 6/24/23.
 */
@Getter
@Setter
@Accessors(chain = true)
public final class QueryDecorator extends C3Decorator {
    @JsonIgnore
    public static final String type = "Query";

    private String statements;

    public QueryDecorator(){
        this.targets = List.of(DecoratorTarget.FUNCTION);
    }
}
