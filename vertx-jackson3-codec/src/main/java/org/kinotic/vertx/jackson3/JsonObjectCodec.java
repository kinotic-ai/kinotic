/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3).
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import io.vertx.core.json.JsonObject;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.Map;

/**
 * Serializes a {@link JsonObject} as its backing map and deserializes a JSON object back into one.
 */
final class JsonObjectCodec {

    private JsonObjectCodec() {
    }

    static final class Serializer extends StdSerializer<JsonObject> {

        Serializer() {
            super(JsonObject.class);
        }

        @Override
        public void serialize(JsonObject value, JsonGenerator jgen, SerializationContext provider) {
            jgen.writePOJO(value.getMap());
        }
    }

    static final class Deserializer extends StdDeserializer<JsonObject> {

        private static final TypeReference<Map<String, Object>> TYPE_REF = new TypeReference<>() {
        };

        Deserializer() {
            super(JsonObject.class);
        }

        @Override
        public JsonObject deserialize(JsonParser p, DeserializationContext ctxt) {
            return new JsonObject(p.<Map<String, Object>>readValueAs(TYPE_REF));
        }
    }
}
