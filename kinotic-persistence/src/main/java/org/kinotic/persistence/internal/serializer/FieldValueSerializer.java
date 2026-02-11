package org.kinotic.persistence.internal.serializer;

import co.elastic.clients.elasticsearch._types.FieldValue;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Created by Navíd Mitchell 🤪 on 11/6/23.
 */
public class FieldValueSerializer extends ValueSerializer<FieldValue> {

    @Override
    public void serialize(FieldValue field, JsonGenerator jsonGenerator, SerializationContext ctxt) throws JacksonException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringProperty("kind", field._kind().name());
        switch (field._kind()) {
            case Double :
                jsonGenerator.writeNumberProperty("value", field.doubleValue());
                break;
            case Long :
                jsonGenerator.writeNumberProperty("value", field.longValue());
                break;
            case Boolean :
                jsonGenerator.writeBooleanProperty("value", field.booleanValue());
                break;
            case String :
                jsonGenerator.writeStringProperty("value", field.stringValue());
                break;
            case Null :
                jsonGenerator.writeNullProperty("value");
                break;
            case Any :
                jsonGenerator.writePOJOProperty("value", field._get());
            default :
                throw new IllegalStateException("Unknown kind " + field._kind());
        }
        jsonGenerator.writeEndObject();
    }

}
