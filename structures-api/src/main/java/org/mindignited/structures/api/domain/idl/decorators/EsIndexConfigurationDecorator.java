package org.mindignited.structures.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.mindignited.continuum.idl.api.schema.decorators.C3Decorator;
import org.mindignited.continuum.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Provides the ability to override the way an Entity Elasticsearch index is configured.
 * Created By Navíd Mitchell 🤪on 3/18/25
 */
@Getter
@Setter
@Accessors(chain = true)
public class EsIndexConfigurationDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "EsIndexConfigurationDecorator";

    private EsIndexConfigurationData value;

    public EsIndexConfigurationDecorator() {
        this.targets = List.of(DecoratorTarget.TYPE);
    }
}
