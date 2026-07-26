/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3).
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import io.vertx.core.json.JsonArray;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

class JsonArraySerializer extends StdSerializer<JsonArray> {

    JsonArraySerializer() {
        super(JsonArray.class);
    }

    @Override
    public void serialize(JsonArray value, JsonGenerator jgen, SerializationContext provider) {
        jgen.writePOJO(value.getList());
    }
}
