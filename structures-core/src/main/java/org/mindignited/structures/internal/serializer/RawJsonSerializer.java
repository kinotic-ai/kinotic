package org.mindignited.structures.internal.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.mindignited.structures.api.domain.RawJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Created by Navíd Mitchell 🤪 on 5/22/23.
 */
public class RawJsonSerializer extends JsonSerializer<RawJson> {

    @Override
    public void serialize(RawJson value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        String json = new String(value.data(), StandardCharsets.UTF_8);
        gen.writeRawValue(json);
    }
}
