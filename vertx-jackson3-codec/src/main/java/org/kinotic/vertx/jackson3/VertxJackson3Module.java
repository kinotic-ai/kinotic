/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3.VertxModule).
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;

/**
 * A Jackson 3 {@code Module} with the Vert.x serializers and deserializers: {@link JsonObject},
 * {@link JsonArray}, {@link Buffer} and {@code byte[]} as base64 (RFC-7493), and {@link Instant} as
 * ISO-8601. Reusable for building custom mappers that must stay wire-compatible with Vert.x JSON.
 */
public class VertxJackson3Module extends SimpleModule {

    public VertxJackson3Module() {
        addSerializer(JsonObject.class, new JsonObjectSerializer());
        addSerializer(JsonArray.class, new JsonArraySerializer());
        addDeserializer(JsonObject.class, new JsonObjectDeserializer());
        addDeserializer(JsonArray.class, new JsonArrayDeserializer());
        addSerializer(Instant.class, new InstantSerializer());
        addDeserializer(Instant.class, new InstantDeserializer());
        addSerializer(byte[].class, new ByteArraySerializer());
        addDeserializer(byte[].class, new ByteArrayDeserializer());
        addSerializer(Buffer.class, new BufferSerializer());
        addDeserializer(Buffer.class, new BufferDeserializer());
    }

}
