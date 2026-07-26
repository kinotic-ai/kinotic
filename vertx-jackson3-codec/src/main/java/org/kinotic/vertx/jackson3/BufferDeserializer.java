/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3).
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import io.vertx.core.buffer.Buffer;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import static io.vertx.core.json.impl.JsonUtil.BASE64_DECODER;

class BufferDeserializer extends StdDeserializer<Buffer> {

    BufferDeserializer() {
        super(Buffer.class);
    }

    @Override
    public Buffer deserialize(JsonParser p, DeserializationContext ctxt) {
        String text = p.getString();
        try {
            return Buffer.buffer(BASE64_DECODER.decode(text));
        } catch (IllegalArgumentException e) {
            throw new InputCoercionException(p, "Expected a base64 encoded byte array", p.currentToken(), Buffer.class);
        }
    }
}
