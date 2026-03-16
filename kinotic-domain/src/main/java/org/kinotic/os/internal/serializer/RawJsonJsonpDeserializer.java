package org.kinotic.os.internal.serializer;

import org.kinotic.os.api.model.RawJson;
import co.elastic.clients.json.JsonpDeserializerBase;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.Jackson3JsonpParser;
import jakarta.json.stream.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;

/**
 * Created by Navíd Mitchell 🤪 on 5/22/23.
 */
public class RawJsonJsonpDeserializer extends JsonpDeserializerBase<RawJson> {

    private final ObjectMapper objectMapper;

    public RawJsonJsonpDeserializer(ObjectMapper objectMapper) {
        super(EnumSet.allOf(JsonParser.Event.class));
        this.objectMapper = objectMapper;
    }

    @Override
    public RawJson deserialize(JsonParser parser, JsonpMapper mapper) {
        Jackson3JsonpParser jacksonJsonpParser;
        if (parser instanceof Jackson3JsonpParser) {
            jacksonJsonpParser = (Jackson3JsonpParser) parser;
        } else {
            throw new IllegalStateException("Expected Jackson3JsonpParser but got " + parser.getClass().getName());
        }
        try {

            tools.jackson.core.JsonParser jacksonParser = jacksonJsonpParser.jacksonParser();
            if (jacksonParser.currentToken() == JsonToken.PROPERTY_NAME
                    && jacksonParser.currentName().equals("_source")) { // What other cases are there?
                jacksonParser.nextToken();
            }

            return RawJson.from(jacksonParser, objectMapper);

        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public RawJson deserialize(JsonParser parser, JsonpMapper mapper, JsonParser.Event event) {
        throw new UnsupportedOperationException();
    }
}
