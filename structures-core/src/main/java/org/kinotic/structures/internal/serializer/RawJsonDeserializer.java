package org.kinotic.structures.internal.serializer;

import java.io.IOException;

import org.kinotic.structures.api.domain.RawJson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Created by Navíd Mitchell 🤪 on 5/22/23.
 */
public class RawJsonDeserializer extends JsonDeserializer<RawJson> {

    private final ObjectMapper objectMapper;

    public RawJsonDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RawJson deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        return RawJson.from(jsonParser, objectMapper);
    }

}
