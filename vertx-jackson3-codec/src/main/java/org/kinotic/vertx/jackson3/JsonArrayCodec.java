/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3).
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import io.vertx.core.json.JsonArray;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.List;

/**
 * Serializes a {@link JsonArray} as its backing list and deserializes a JSON array back into one.
 */
final class JsonArrayCodec {

    private JsonArrayCodec() {
    }

    static final class Serializer extends StdSerializer<JsonArray> {

        Serializer() {
            super(JsonArray.class);
        }

        @Override
        public void serialize(JsonArray value, JsonGenerator jgen, SerializationContext provider) {
            jgen.writePOJO(value.getList());
        }
    }

    static final class Deserializer extends StdDeserializer<JsonArray> {

        private static final TypeReference<List<Object>> TYPE_REF = new TypeReference<>() {
        };

        Deserializer() {
            super(JsonArray.class);
        }

        @Override
        public JsonArray deserialize(JsonParser p, DeserializationContext ctxt) {
            return new JsonArray(p.<List<Object>>readValueAs(TYPE_REF));
        }
    }
}
