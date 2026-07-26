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

}
