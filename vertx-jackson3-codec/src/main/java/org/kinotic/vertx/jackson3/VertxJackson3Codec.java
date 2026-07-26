package org.kinotic.vertx.jackson3;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.json.JsonCodec;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * A Vert.x {@link JsonCodec} backed by a Jackson 3 {@link ObjectMapper} built from the
 * {@link VertxJackson3Module} plus every registered {@link Jackson3MapperCustomizer}. Decoding and encoding
 * follow the Vert.x data-binding conventions: decoding to {@link Object} yields {@link JsonObject} /
 * {@link JsonArray} for JSON objects and arrays, and trailing input after a decoded value is rejected.
 */
public class VertxJackson3Codec implements JsonCodec {

    private static final ObjectMapper MAPPER = buildMapper();

    /**
     * @return the {@link ObjectMapper} used for data binding
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ObjectMapper buildMapper() {
        JsonMapper.Builder builder = JsonMapper.builder().addModule(new VertxJackson3Module());
        for (Jackson3MapperCustomizer customizer : ServiceLoader.load(Jackson3MapperCustomizer.class)) {
            customizer.customize(builder);
        }
        return builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromValue(Object json, Class<T> clazz) {
        T value = MAPPER.convertValue(json, clazz);
        if (clazz == Object.class) {
            value = (T) adapt(value);
        }
        return value;
    }

    @Override
    public <T> T fromString(String json, Class<T> clazz) throws DecodeException {
        return fromParser(MAPPER.createParser(json), clazz);
    }

    @Override
    public <T> T fromBuffer(Buffer json, Class<T> clazz) throws DecodeException {
        return fromParser(MAPPER.createParser(json.getBytes()), clazz);
    }

    @Override
    public String toString(Object object, boolean pretty) throws EncodeException {
        String ret;
        try {
            if (pretty) {
                ret = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
            } else {
                ret = MAPPER.writeValueAsString(object);
            }
        } catch (Exception e) {
            throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
        }
        return ret;
    }

    @Override
    public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
        byte[] encoded;
        try {
            if (pretty) {
                encoded = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(object);
            } else {
                encoded = MAPPER.writeValueAsBytes(object);
            }
        } catch (Exception e) {
            throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
        }
        return Buffer.buffer(encoded);
    }

    @SuppressWarnings("unchecked")
    private static <T> T fromParser(JsonParser parser, Class<T> type) throws DecodeException {
        T value;
        JsonToken remaining;
        try {
            value = MAPPER.readValue(parser, type);
            remaining = parser.nextToken();
        } catch (Exception e) {
            throw new DecodeException("Failed to decode:" + e.getMessage(), e);
        } finally {
            try {
                parser.close();
            } catch (Exception ignore) {
                // a parser over an in-memory source has nothing meaningful to fail on close
            }
        }
        if (remaining != null) {
            throw new DecodeException("Unexpected trailing token");
        }
        if (type == Object.class) {
            value = (T) adapt(value);
        }
        return value;
    }

    // databind produces Map/List for untyped JSON; the Vert.x convention is JsonObject/JsonArray wrappers
    @SuppressWarnings("unchecked")
    private static Object adapt(Object o) {
        Object ret = o;
        if (o instanceof List<?> list) {
            ret = new JsonArray(list);
        } else if (o instanceof Map) {
            ret = new JsonObject((Map<String, Object>) o);
        }
        return ret;
    }

}
