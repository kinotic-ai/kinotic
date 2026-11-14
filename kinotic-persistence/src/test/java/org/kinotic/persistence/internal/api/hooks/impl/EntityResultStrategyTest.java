package org.kinotic.persistence.internal.api.hooks.impl;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.persistence.internal.api.services.ElasticVersion;
import org.kinotic.persistence.internal.api.services.json.JsonStreamProcessor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins what an entity's stored document looks like when it is written out to a caller: the document as stored,
 * with the version the entity is at under the entity's version field.
 */
public class EntityResultStrategyTest {

    private static final ElasticVersion VERSION = new ElasticVersion(3, 17);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final JsonStreamProcessor<RawJson, ElasticVersion, RawJson> versioned =
            JsonStreamProcessor.create(EntityResultStrategy.toRawJson(jsonMapper, "version"));
    private final JsonStreamProcessor<RawJson, ElasticVersion, RawJson> unversioned =
            JsonStreamProcessor.create(EntityResultStrategy.toRawJson(jsonMapper, null));

    @Test
    public void theVersionIsAddedToTheDocumentAsStored() {
        assertEquals("{\"id\":\"a\",\"price\":1.5,\"big\":12345678901234567890,\"small\":1.0E-10,\"none\":null,\"on\":true,"
                     + "\"owner\":{\"id\":\"p\",\"version\":\"theirs\"},\"tags\":[\"x\",{\"version\":1},[1,[2]]],\"empty\":{},\"version\":\"3:17\"}",
                     text(versioned.process(raw("{\"id\":\"a\",\"price\":1.5,\"big\":12345678901234567890,\"small\":1.0E-10,\"none\":null,\"on\":true,"
                                                + "\"owner\":{\"id\":\"p\",\"version\":\"theirs\"},\"tags\":[\"x\",{\"version\":1},[1,[2]]],\"empty\":{}}"),
                                            VERSION)),
                     "Every field is written as stored, a nested field named like the version field included");

        assertEquals("{\"version\":\"3:17\"}", text(versioned.process(raw("{}"), VERSION)));

        assertEquals("{\"text\":\"unié🤪 \\\"q\\\" \\n\",\"version\":\"3:17\"}",
                     text(versioned.process(raw("{\"text\":\"unié🤪 \\\"q\\\" \\n\"}"), VERSION)));
    }

    @Test
    public void aStoredValueUnderTheVersionFieldIsReplaced() {
        assertEquals("{\"id\":\"a\",\"make\":\"Audi\",\"version\":\"3:17\"}",
                     text(versioned.process(raw("{\"id\":\"a\",\"version\":\"1:1\",\"make\":\"Audi\"}"), VERSION)));

        assertEquals("{\"id\":\"a\",\"make\":\"Audi\",\"version\":\"3:17\"}",
                     text(versioned.process(raw("{\"id\":\"a\",\"version\":{\"nested\":[1,2]},\"make\":\"Audi\"}"), VERSION)),
                     "A stored value of any shape is left out");
    }

    @Test
    public void anEntityWithoutAVersionFieldIsWrittenAsStored() {
        assertEquals("{\"id\":\"a\",\"version\":\"mine\",\"n\":1.5}",
                     text(unversioned.process(raw("{\"id\":\"a\",\"version\":\"mine\",\"n\":1.5}"), null)));
    }

    @Test
    public void theDocumentCanBeWrittenAsATokenBuffer() {
        JsonStreamProcessor<RawJson, ElasticVersion, TokenBuffer> toTokenBuffer =
                JsonStreamProcessor.create(EntityResultStrategy.toTokenBuffer(jsonMapper, "version"));

        TokenBuffer written = toTokenBuffer.process(raw("{\"id\":\"a\",\"version\":\"1:1\",\"owner\":{\"id\":\"p\"},\"tags\":[\"x\"]}"), VERSION);

        assertEquals(jsonMapper.readTree("{\"id\":\"a\",\"owner\":{\"id\":\"p\"},\"tags\":[\"x\"],\"version\":\"3:17\"}"),
                     jsonMapper.readTree(written.asParser()));

        JsonStreamProcessor<RawJson, ElasticVersion, TokenBuffer> unversionedToTokenBuffer =
                JsonStreamProcessor.create(EntityResultStrategy.toTokenBuffer(jsonMapper, null));
        assertEquals(jsonMapper.readTree("{\"id\":\"a\",\"tags\":[\"x\"]}"),
                     jsonMapper.readTree(unversionedToTokenBuffer.process(raw("{\"id\":\"a\",\"tags\":[\"x\"]}"), null).asParser()));
    }

    @Test
    public void aDocumentThatIsNotAnObjectIsRejected() {
        assertInstanceOf(IllegalArgumentException.class,
                         assertThrows(RuntimeException.class, () -> versioned.process(raw("[{\"id\":\"a\"}]"), VERSION)));
    }

    private static RawJson raw(String json) {
        return new RawJson(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(RawJson rawJson) {
        return new String(rawJson.data(), StandardCharsets.UTF_8);
    }
}
