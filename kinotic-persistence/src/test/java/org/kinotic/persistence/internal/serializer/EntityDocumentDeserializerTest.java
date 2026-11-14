package org.kinotic.persistence.internal.serializer;

import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;
import org.kinotic.persistence.internal.api.services.EntityDocument;
import tools.jackson.databind.json.JsonMapper;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins what is read of an entity's document from an Elasticsearch response: the document as stored without any
 * value under the version field, and the tenant it names.
 */
public class EntityDocumentDeserializerTest {

    private static final String STORED = "{\"id\":\"a\",\"n\":1.5,\"big\":12345678901234567890,\"none\":null,\"on\":true,"
            + "\"owner\":{\"id\":\"p\",\"version\":\"theirs\",\"tenantId\":\"other\"},\"tags\":[\"x\",{\"version\":1},[1,[2]]],\"empty\":{},"
            + "\"text\":\"unié🤪 \\\"q\\\" \\n\",\"tenantId\":\"tenant1\"}";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Jackson3JsonpMapper jsonpMapper = new Jackson3JsonpMapper(jsonMapper);

    @Test
    public void theDocumentIsReadAsStoredWithItsTenant() {
        EntityDocument document = read(new EntityDocumentDeserializer(jsonMapper, "version", "tenantId"), STORED);

        assertEquals(STORED, text(document));
        assertEquals("tenant1", document.tenantId(), "The tenant is the top-level field, whatever nested fields are named");
    }

    @Test
    public void aValueStoredUnderTheVersionFieldIsLeftOut() {
        EntityDocumentDeserializer deserializer = new EntityDocumentDeserializer(jsonMapper, "version", "tenantId");

        assertEquals("{\"id\":\"a\",\"make\":\"Audi\"}", text(read(deserializer, "{\"id\":\"a\",\"version\":\"1:1\",\"make\":\"Audi\"}")));
        assertEquals("{\"id\":\"a\",\"make\":\"Audi\"}", text(read(deserializer, "{\"version\":{\"nested\":[1,{\"a\":2}]},\"id\":\"a\",\"make\":\"Audi\"}")));
        assertEquals("{}", text(read(deserializer, "{\"version\":null}")));
        assertEquals("{\"id\":\"a\",\"owner\":{\"version\":\"theirs\"}}", text(read(deserializer, "{\"id\":\"a\",\"owner\":{\"version\":\"theirs\"}}")),
                     "A nested field of the same name is part of the document");
    }

    @Test
    public void aDocumentNamingNoTenantHasNone() {
        EntityDocumentDeserializer deserializer = new EntityDocumentDeserializer(jsonMapper, null, "tenantId");

        assertNull(read(deserializer, "{\"id\":\"a\"}").tenantId());
        assertNull(read(deserializer, "{\"id\":\"a\",\"tenantId\":null}").tenantId());
        EntityDocument numbered = read(deserializer, "{\"id\":\"a\",\"tenantId\":7}");
        assertNull(numbered.tenantId(), "Only a string names a tenant");
        assertEquals("{\"id\":\"a\",\"tenantId\":7}", text(numbered), "The field is still part of the document");
    }

    @Test
    public void anEntityWithNeitherFieldIsReadWhole() {
        EntityDocument document = read(new EntityDocumentDeserializer(jsonMapper, null, null), "{\"id\":\"a\",\"version\":\"mine\",\"tenantId\":\"x\"}");

        assertEquals("{\"id\":\"a\",\"version\":\"mine\",\"tenantId\":\"x\"}", text(document));
        assertNull(document.tenantId());
    }

    @Test
    public void theSourceIsReadFromWhereElasticsearchsClientLeavesTheParser() {
        // within a hit the client hands over the parser still on the "_source" property name
        try (JsonParser parser = jsonpMapper.jsonProvider().createParser(new StringReader("{\"_source\":{\"id\":\"a\",\"tenantId\":\"tenant1\"},\"after\":1}"))) {
            parser.next();
            parser.next();

            EntityDocument document = new EntityDocumentDeserializer(jsonMapper, "version", "tenantId").deserialize(parser, jsonpMapper);

            assertEquals("{\"id\":\"a\",\"tenantId\":\"tenant1\"}", text(document));
            assertEquals("tenant1", document.tenantId());
            assertEquals(JsonParser.Event.KEY_NAME, parser.next(), "The parser is left at the end of the source");
            assertEquals("after", parser.getString());
        }
    }

    private EntityDocument read(EntityDocumentDeserializer deserializer, String source) {
        try (JsonParser parser = jsonpMapper.jsonProvider().createParser(new StringReader(source))) {
            parser.next();
            return deserializer.deserialize(parser, jsonpMapper);
        }
    }

    private static String text(EntityDocument document) {
        return new String(document.json().data(), StandardCharsets.UTF_8);
    }
}
