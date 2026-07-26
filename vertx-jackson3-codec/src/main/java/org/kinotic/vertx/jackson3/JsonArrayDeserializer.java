/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3).
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import io.vertx.core.json.JsonArray;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.List;

class JsonArrayDeserializer extends StdDeserializer<JsonArray> {

    private static final TypeReference<List<Object>> TYPE_REF = new TypeReference<>() {
    };

    JsonArrayDeserializer() {
        super(JsonArray.class);
    }

    @Override
    public JsonArray deserialize(JsonParser p, DeserializationContext ctxt) {
        return new JsonArray(p.<List<Object>>readValueAs(TYPE_REF));
    }
}
