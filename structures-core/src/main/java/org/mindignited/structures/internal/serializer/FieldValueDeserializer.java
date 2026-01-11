package org.mindignited.structures.internal.serializer;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.Validate;

import java.io.IOException;

/**
 * Created by Navíd Mitchell 🤪 on 11/6/23.
 */
public class FieldValueDeserializer extends JsonDeserializer<FieldValue> {

    @Override
    public FieldValue deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JacksonException {
        JsonNode node = jp.getCodec().readTree(jp);
        Validate.isTrue(node.has("kind"), "kind missing from FieldValue");
        Validate.isTrue(node.has("value"), "value missing from FieldValue");

        String kindString = node.get("kind").textValue();
        FieldValue.Kind kind = FieldValue.Kind.valueOf(kindString);
        switch (kind){
            case Double:
                return FieldValue.of(node.get("value").doubleValue());
            case Long:
                return FieldValue.of(node.get("value").longValue());
            case Boolean:
                return FieldValue.of(node.get("value").booleanValue());
            case String:
                return FieldValue.of(node.get("value").textValue());
            case Null:
                return FieldValue.NULL;
            case Any:
                JsonData jsonData = JsonData.fromJson(node.get("value").toString());
                return FieldValue.of(jsonData);
            default:
                throw new IllegalStateException("Unknown kind " + kind);
        }
    }
}
