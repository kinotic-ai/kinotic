package org.kinotic.persistence.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.RawJson;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins how the version an entity is at is written into a document on its way out to a caller.
 */
public class EntityVersionWriterTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final EntityVersionWriter writer = new EntityVersionWriter(jsonMapper, "version");

    @Test
    public void theVersionIsWrittenAsTheLastField() {
        assertEquals("{\"id\":\"a\",\"owner\":{\"version\":\"theirs\"},\"tags\":[\"x\",{}],\"version\":\"3:17\"}",
                     text(writer.write(raw("{\"id\":\"a\",\"owner\":{\"version\":\"theirs\"},\"tags\":[\"x\",{}]}"), 3L, 17L)),
                     "The rest of the document is returned byte for byte");

        assertEquals("{\"version\":\"3:17\"}", text(writer.write(raw("{}"), 3L, 17L)), "An empty document gets no separator");

        assertEquals("{\"text\":\"ends with a brace }\",\"version\":\"12:3456789\"}",
                     text(writer.write(raw("{\"text\":\"ends with a brace }\"}"), 12L, 3456789L)));
    }

    @Test
    public void theFieldNameIsWrittenAsJson() {
        EntityVersionWriter quoted = new EntityVersionWriter(jsonMapper, "ver\"sion");

        RawJson written = quoted.write(raw("{\"id\":\"a\"}"), 1L, 2L);

        assertEquals("1:2", jsonMapper.readTree(written.data()).get("ver\"sion").asString());
    }

    @Test
    public void anIndentedDocumentStaysValid() {
        assertEquals(jsonMapper.readTree("{\"id\":\"a\",\"version\":\"3:17\"}"),
                     jsonMapper.readTree(writer.write(raw("{\n  \"id\" : \"a\"\n}\n"), 3L, 17L).data()));
        assertEquals(jsonMapper.readTree("{\"version\":\"3:17\"}"),
                     jsonMapper.readTree(writer.write(raw("{ }"), 3L, 17L).data()));
    }

    @Test
    public void anEntityWithoutAVersionFieldGetsItsDocumentBack() {
        EntityVersionWriter unversioned = new EntityVersionWriter(jsonMapper, null);
        RawJson json = raw("{\"id\":\"a\"}");

        assertSame(json, unversioned.write(json, 3L, 17L));
    }

    @Test
    public void aDocumentThatIsNotAnObjectIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> writer.write(raw("[1,2]"), 3L, 17L));
        assertThrows(IllegalArgumentException.class, () -> writer.write(raw(""), 3L, 17L));
        assertThrows(IllegalArgumentException.class, () -> writer.write(raw("}"), 3L, 17L));
    }

    private static RawJson raw(String json) {
        return new RawJson(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(RawJson rawJson) {
        return new String(rawJson.data(), StandardCharsets.UTF_8);
    }
}
