package org.mindignited.structures.internal.serializer;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.json.JsonData;
import org.apache.commons.lang3.Validate;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Created by Navíd Mitchell 🤪 on 11/6/23.
 */
public class FieldValueDeserializer extends ValueDeserializer<FieldValue> {

    @Override
    public FieldValue deserialize(JsonParser jp, DeserializationContext ctxt) {
        JsonNode node = jp.objectReadContext().readTree(jp);
        Validate.isTrue(node.has("kind"), "kind missing from FieldValue");
        Validate.isTrue(node.has("value"), "value missing from FieldValue");

        String kindString = node.get("kind").asString();
        FieldValue.Kind kind = FieldValue.Kind.valueOf(kindString);
        switch (kind){
            case Double:
                return FieldValue.of(node.get("value").doubleValue());
            case Long:
                return FieldValue.of(node.get("value").longValue());
            case Boolean:
                return FieldValue.of(node.get("value").booleanValue());
            case String:
                // FIXME: make sure we want a null string to be represented as FieldValue.NULL
                if(node.get("value").isNull()){
                    return FieldValue.NULL;
                }else{
                    return FieldValue.of(node.get("value").asString());
                }
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
