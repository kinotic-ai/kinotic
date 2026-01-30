package org.mindignited.structures.internal.serializer;

import org.mindignited.structures.api.domain.FastestType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Created By Navíd Mitchell 🤪on 2/3/25
 */
public class FastestTypeSerializer  extends ValueSerializer<FastestType> {

    @Override
    public void serialize(FastestType value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writePOJO(value.data());
    }

}
