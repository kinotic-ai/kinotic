package org.kinotic.persistence.internal.serializer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import org.kinotic.persistence.api.domain.FastestType;

/**
 * Created By Navíd Mitchell 🤪on 2/3/25
 */
public class FastestTypeSerializer  extends ValueSerializer<FastestType> {

    @Override
    public void serialize(FastestType value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writePOJO(value.data());
    }

}
