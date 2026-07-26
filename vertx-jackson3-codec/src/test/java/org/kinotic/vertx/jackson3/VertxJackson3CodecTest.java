package org.kinotic.vertx.jackson3;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Verifies the codec against the Vert.x data-binding conventions, through the same {@link Json} entry points
 * Vert.x itself dispatches to once the factory is selected from {@code META-INF/services}.
 */
public class VertxJackson3CodecTest {

    @Test
    public void factoryIsSelectedByVertx() {
        Assertions.assertInstanceOf(VertxJackson3Codec.class, io.vertx.core.spi.JsonFactory.load().codec());
    }

    @Test
    public void decodesUntypedJsonToVertxWrappers() {
        Object object = Json.decodeValue("{\"name\":\"a\",\"nested\":{\"n\":1},\"list\":[1,2]}");
        Assertions.assertInstanceOf(JsonObject.class, object);
        JsonObject jsonObject = (JsonObject) object;
        Assertions.assertEquals(1, jsonObject.getJsonObject("nested").getInteger("n"));
        Assertions.assertEquals(new JsonArray(List.of(1, 2)), jsonObject.getJsonArray("list"));

        Assertions.assertInstanceOf(JsonArray.class, Json.decodeValue("[1,2]"));
    }

    @Test
    public void bindsPojos() {
        TestPojo pojo = Json.decodeValue("{\"name\":\"a\",\"count\":2}", TestPojo.class);
        Assertions.assertEquals("a", pojo.getName());
        Assertions.assertEquals(2, pojo.getCount());
        Assertions.assertEquals(new JsonObject().put("name", "a").put("count", 2),
                                Json.decodeValue(Json.encode(pojo), JsonObject.class));
    }

    @Test
    public void rejectsTrailingInput() {
        Assertions.assertThrows(DecodeException.class, () -> Json.decodeValue("{} {}", JsonObject.class));
    }

    @Test
    public void rejectsMalformedJson() {
        Assertions.assertThrows(DecodeException.class, () -> Json.decodeValue("{\"a\":"));
        Assertions.assertThrows(DecodeException.class, () -> Json.decodeValue("not json", JsonObject.class));
    }

    @Test
    public void roundTripsThroughBuffers() {
        JsonObject jsonObject = new JsonObject().put("k", "v").put("n", 1);
        Buffer buffer = Json.encodeToBuffer(jsonObject);
        Assertions.assertEquals(jsonObject, Json.decodeValue(buffer, JsonObject.class));
        Assertions.assertInstanceOf(JsonObject.class, Json.decodeValue(buffer));
    }

    @Test
    public void prettyPrints() {
        JsonObject jsonObject = new JsonObject().put("a", 1).put("b", 2);
        String pretty = Json.encodePrettily(jsonObject);
        Assertions.assertTrue(pretty.contains("\n"), "expected pretty output, got: " + pretty);
        Assertions.assertEquals(jsonObject, Json.decodeValue(pretty, JsonObject.class));
    }

    @Test
    public void bindsVertxTypesInsidePojos() {
        TestEnvelope envelope = new TestEnvelope();
        envelope.setPayload(new JsonObject().put("k", "v"));
        envelope.setRaw(new byte[]{1, 2, 3});
        envelope.setBuffer(Buffer.buffer(new byte[]{4, 5}));
        envelope.setWhen(Instant.parse("2026-07-26T12:00:00Z"));

        TestEnvelope decoded = Json.decodeValue(Json.encode(envelope), TestEnvelope.class);
        Assertions.assertEquals(envelope.getPayload(), decoded.getPayload());
        Assertions.assertArrayEquals(envelope.getRaw(), decoded.getRaw());
        Assertions.assertEquals(envelope.getBuffer(), decoded.getBuffer());
        Assertions.assertEquals(envelope.getWhen(), decoded.getWhen());
    }

    @Test
    public void preservesNumericTypes() {
        JsonObject decoded = (JsonObject) Json.decodeValue("{\"small\":1,\"big\":9007199254740993,\"decimal\":1.5}");
        Assertions.assertEquals(1, decoded.getInteger("small"));
        // past 2^53, a codec that routes numbers through double corrupts the value
        Assertions.assertEquals(9007199254740993L, decoded.getLong("big"));
        Assertions.assertEquals(1.5d, decoded.getDouble("decimal"));
    }

    @Test
    public void handlesNullValues() {
        Assertions.assertEquals("null", Json.encode(null));
        Assertions.assertNull(Json.decodeValue("null"));
    }

    @Test
    public void encodesVertxTypesPerRfc7493() {
        Instant instant = Instant.parse("2026-07-26T12:00:00Z");
        byte[] bytes = new byte[]{1, 2, 3};
        JsonObject jsonObject = new JsonObject()
                .put("when", instant)
                .put("raw", bytes)
                .put("buffer", Buffer.buffer(bytes));

        JsonObject decoded = Json.decodeValue(Json.encode(jsonObject), JsonObject.class);
        Assertions.assertEquals(instant, decoded.getInstant("when"));
        Assertions.assertArrayEquals(bytes, decoded.getBinary("raw"));
        Assertions.assertEquals(Buffer.buffer(bytes), decoded.getBuffer("buffer"));
        // RFC-7493 base64url without padding, the encoding JsonObject.getBinary expects
        Assertions.assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                                Json.decodeValue(Json.encode(jsonObject), JsonObject.class).getString("raw"));
    }

    @Test
    public void convertsValuesBetweenShapes() {
        VertxJackson3Codec codec = new VertxJackson3Codec();
        TestPojo pojo = codec.fromValue(Map.of("name", "a", "count", 3), TestPojo.class);
        Assertions.assertEquals(3, pojo.getCount());
        Assertions.assertInstanceOf(JsonObject.class, codec.fromValue(Map.of("k", "v"), Object.class));
    }

    @Test
    public void appliesRegisteredCustomizers() {
        // TestMapperCustomizer registers TestCustomType's serializer via META-INF/services
        Assertions.assertEquals("\"customized\"", Json.encode(new TestCustomType()));
    }

    @Test
    public void acceptsACompleteMapper() {
        var original = VertxJackson3Codec.mapper();
        try {
            var provided = tools.jackson.databind.json.JsonMapper.builder()
                                                                 .addModule(new VertxJackson3Module())
                                                                 .addModule(TestMapperCustomizer.markerModule("provided"))
                                                                 .build();
            VertxJackson3Codec.setMapper(provided);
            Assertions.assertSame(provided, VertxJackson3Codec.mapper());
            Assertions.assertEquals("\"provided\"", Json.encode(new TestCustomType()));
        } finally {
            VertxJackson3Codec.setMapper(original);
        }
        Assertions.assertEquals("\"customized\"", Json.encode(new TestCustomType()));
    }

}
