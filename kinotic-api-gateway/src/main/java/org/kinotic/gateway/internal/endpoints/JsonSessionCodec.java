package org.kinotic.gateway.internal.endpoints;

import io.vertx.ext.web.Session;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores and loads POJO values in a Vert.x web {@link Session} as JSON strings.
 *
 * <p>A clustered session store marshals every attribute through a fixed set of supported
 * types — primitives, {@code String}, {@code Buffer}, {@code byte[]}, {@code ClusterSerializable} —
 * and rejects anything else, so an application POJO must be encoded to a {@code String} to survive
 * a round-trip across the cluster. Encoding through the configured {@link JsonMapper} also preserves
 * the polymorphic-type handling (such as the {@code Participant} subtype discriminator) that raw
 * session marshalling would have no way to reconstruct.
 */
@Slf4j
public final class JsonSessionCodec {

    private JsonSessionCodec() {
    }

    /** Serializes {@code value} to JSON and stores it under {@code key}. */
    public static void store(Session session, JsonMapper jsonMapper, String key, Object value) {
        session.put(key, jsonMapper.writeValueAsString(value));
    }

    /**
     * Reads and deserializes the value at {@code key}, leaving it in the session. Returns
     * {@code null} when the key is absent or its value cannot be parsed.
     */
    public static <T> T read(Session session, JsonMapper jsonMapper, String key, Class<T> type) {
        Object raw = session == null ? null : session.get(key);
        return decode(raw, jsonMapper, type);
    }

    /**
     * Reads, deserializes, and removes the value at {@code key} — for single-use values such as a
     * one-shot OAuth flow nonce. Returns {@code null} when the key is absent or its value cannot be
     * parsed.
     */
    public static <T> T remove(Session session, JsonMapper jsonMapper, String key, Class<T> type) {
        Object raw = session == null ? null : session.remove(key);
        return decode(raw, jsonMapper, type);
    }

    private static <T> T decode(Object raw, JsonMapper jsonMapper, Class<T> type) {
        if (!(raw instanceof String json) || json.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException e) {
            // A corrupt value can't authenticate or authorize anything, so fail closed to "absent".
            log.debug("Discarding unreadable session value for {}", type.getSimpleName(), e);
            return null;
        }
    }
}
