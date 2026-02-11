package org.kinotic.persistence.internal.idl.converters.openapi;

import io.swagger.v3.oas.models.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.internal.idl.converters.common.BaseConversionState;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Navíd Mitchell 🤪 on 5/14/23.
 */
@Getter
@Setter
@Accessors(chain = true)
public class OpenApiConversionState extends BaseConversionState {

    private final Map<String, Schema<?>> referencedSchemas = new HashMap<>();

    public OpenApiConversionState(StructuresProperties structuresProperties) {
        super(structuresProperties);
    }

    public OpenApiConversionState addReferencedSchema(String name, Schema<?> schema){
        referencedSchemas.put(name, schema);
        return this;
    }

}
