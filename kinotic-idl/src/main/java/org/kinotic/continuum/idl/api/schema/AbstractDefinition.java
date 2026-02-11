package org.kinotic.continuum.idl.api.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.decorators.C3Decorator;

import java.util.List;
import java.util.Map;

/**
 * Bases object for everything needing decorators and metadata
 * Created by Navíd Mitchell 🤪 on 4/16/24.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY) // do not include any empty or null fields
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
public abstract class AbstractDefinition implements HasDecorators, HasMetadata {

    /**
     * The list of {@link C3Decorator}s that should be applied to this type
     */
    protected List<C3Decorator> decorators = null;

    /**
     * The metadata keyword is legal on any {@link AbstractDefinition}, The objects provided must be serializable to JSON.
     * Usually, metadata is for putting things like descriptions or hints for code generators, or other things tools can use.
     */
    protected Map<String, ?> metadata = null;

}
