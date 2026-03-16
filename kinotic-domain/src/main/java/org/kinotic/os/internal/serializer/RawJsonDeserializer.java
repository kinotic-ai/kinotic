package org.kinotic.os.internal.serializer;

import org.kinotic.os.api.model.RawJson;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;

/**
 * Created by Navíd Mitchell 🤪 on 5/22/23.
 */
public class RawJsonDeserializer extends ValueDeserializer<RawJson> {

    private final ObjectMapper objectMapper;

    public RawJsonDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RawJson deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws JacksonException {
        return RawJson.from(jsonParser, objectMapper);
    }

}
