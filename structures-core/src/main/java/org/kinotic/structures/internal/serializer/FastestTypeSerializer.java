package org.kinotic.structures.internal.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.kinotic.structures.api.domain.FastestType;

import java.io.IOException;

/**
 * Created By Navíd Mitchell 🤪on 2/3/25
 */
public class FastestTypeSerializer  extends JsonSerializer<FastestType> {

    @Override
    public void serialize(FastestType value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeObject(value.data());
    }
}
