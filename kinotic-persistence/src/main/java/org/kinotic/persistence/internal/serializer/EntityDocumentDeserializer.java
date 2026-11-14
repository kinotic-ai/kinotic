package org.kinotic.persistence.internal.serializer;

import co.elastic.clients.json.JsonpDeserializerBase;
import co.elastic.clients.json.JsonpMapper;
import jakarta.json.stream.JsonParser;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.internal.serializer.RawJsonJsonpDeserializer;
import org.kinotic.persistence.internal.api.services.EntityDocument;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonToken;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumSet;

/**
 * Reads the source of an entity's document out of an Elasticsearch response as an {@link EntityDocument}. The
 * document is read as stored, except that a value stored under the entity's version field is left out, and the
 * tenant it names is read along the way.
 */
public class EntityDocumentDeserializer extends JsonpDeserializerBase<EntityDocument> {

    private final JsonMapper jsonMapper;
    private final String versionFieldName;
    private final String tenantFieldName;

    /**
     * @param versionFieldName the entity's version field, or null for an entity without one
     * @param tenantFieldName  the field holding a document's tenant, or null for an entity that is not separated by tenant
     */
    public EntityDocumentDeserializer(JsonMapper jsonMapper, String versionFieldName, String tenantFieldName) {
        super(EnumSet.allOf(JsonParser.Event.class));
        this.jsonMapper = jsonMapper;
        this.versionFieldName = versionFieldName;
        this.tenantFieldName = tenantFieldName;
    }

    @Override
    public EntityDocument deserialize(JsonParser parser, JsonpMapper mapper) {
        try {
            tools.jackson.core.JsonParser jacksonParser = RawJsonJsonpDeserializer.sourceParser(parser);
            EntityDocument ret;
            if (jacksonParser.currentToken() == JsonToken.START_OBJECT && (versionFieldName != null || tenantFieldName != null)) {
                ret = readFields(jacksonParser);
            } else {
                ret = new EntityDocument(RawJson.from(jacksonParser, jsonMapper), null);
            }
            return ret;
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public EntityDocument deserialize(JsonParser parser, JsonpMapper mapper, JsonParser.Event event) {
        throw new UnsupportedOperationException();
    }

    private EntityDocument readFields(tools.jackson.core.JsonParser parser) {
        String tenantId = null;
        try (ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder()) {
            try (JsonGenerator jsonGenerator = jsonMapper.createGenerator(byteArrayBuilder, JsonEncoding.UTF8)) {
                jsonGenerator.writeStartObject();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.currentName();
                    if (fieldName.equals(versionFieldName)) {
                        parser.nextToken();
                        parser.skipChildren();
                    } else if (fieldName.equals(tenantFieldName)) {
                        jsonGenerator.writeName(fieldName);
                        if (parser.nextToken() == JsonToken.VALUE_STRING) {
                            tenantId = parser.getString();
                        }
                        jsonGenerator.copyCurrentStructure(parser);
                    } else {
                        // on a property name this copies the name and the whole of its value
                        jsonGenerator.copyCurrentStructure(parser);
                    }
                }
                jsonGenerator.writeEndObject();
            }
            return new EntityDocument(new RawJson(byteArrayBuilder.toByteArray()), tenantId);
        }
    }
}
