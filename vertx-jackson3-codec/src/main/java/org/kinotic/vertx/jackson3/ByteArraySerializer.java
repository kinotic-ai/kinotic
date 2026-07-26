/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3).
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import static io.vertx.core.json.impl.JsonUtil.BASE64_ENCODER;

class ByteArraySerializer extends StdSerializer<byte[]> {

    ByteArraySerializer() {
        super(byte[].class);
    }

    @Override
    public void serialize(byte[] value, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeString(BASE64_ENCODER.encodeToString(value));
    }
}
