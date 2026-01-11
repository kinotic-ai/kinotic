package org.mindignited.structures.internal.serializer;

import co.elastic.clients.elasticsearch._types.FieldValue;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Created by Navíd Mitchell 🤪 on 11/6/23.
 */
public class FieldValueSerializer extends JsonSerializer<FieldValue> {

    @Override
    public void serialize(FieldValue field, JsonGenerator jsonGenerator, SerializerProvider serializers) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("kind", field._kind().name());
        switch (field._kind()) {
            case Double :
                jsonGenerator.writeNumberField("value", field.doubleValue());
                break;
            case Long :
                jsonGenerator.writeNumberField("value", field.longValue());
                break;
            case Boolean :
                jsonGenerator.writeBooleanField("value", field.booleanValue());
                break;
            case String :
                jsonGenerator.writeStringField("value", field.stringValue());
                break;
            case Null :
                jsonGenerator.writeNullField("value");
                break;
            case Any :
                jsonGenerator.writeObjectField("value", field._get());
            default :
                throw new IllegalStateException("Unknown kind " + field._kind());
        }
        jsonGenerator.writeEndObject();
    }
}
