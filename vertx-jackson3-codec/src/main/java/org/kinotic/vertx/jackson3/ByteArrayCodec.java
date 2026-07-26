/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3).
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

import static io.vertx.core.json.impl.JsonUtil.BASE64_DECODER;
import static io.vertx.core.json.impl.JsonUtil.BASE64_ENCODER;

/**
 * Serializes a {@code byte[]} as a base64url string and deserializes one back, per RFC-7493.
 */
final class ByteArrayCodec {

    private ByteArrayCodec() {
    }

    static final class Serializer extends StdSerializer<byte[]> {

        Serializer() {
            super(byte[].class);
        }

        @Override
        public void serialize(byte[] value, JsonGenerator jgen, SerializationContext provider) {
            jgen.writeString(BASE64_ENCODER.encodeToString(value));
        }
    }

    static final class Deserializer extends StdDeserializer<byte[]> {

        Deserializer() {
            super(byte[].class);
        }

        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) {
            String text = p.getString();
            try {
                return BASE64_DECODER.decode(text);
            } catch (IllegalArgumentException e) {
                throw new InputCoercionException(p, "Expected a base64 encoded byte array", p.currentToken(), byte[].class);
            }
        }
    }
}
