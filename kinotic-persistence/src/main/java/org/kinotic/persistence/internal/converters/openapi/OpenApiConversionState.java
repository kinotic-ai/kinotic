package org.kinotic.persistence.internal.converters.openapi;

import io.swagger.v3.oas.models.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.internal.converters.common.BaseConversionState;

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

    public OpenApiConversionState(PersistenceProperties persistenceProperties) {
        super(persistenceProperties);
    }

    public OpenApiConversionState addReferencedSchema(String name, Schema<?> schema){
        referencedSchemas.put(name, schema);
        return this;
    }

}
