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

import java.time.DateTimeException;
import java.time.Instant;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * Serializes an {@link Instant} as an ISO-8601 string and deserializes one back, per RFC-7493.
 */
final class InstantCodec {

    private InstantCodec() {
    }

    static final class Serializer extends StdSerializer<Instant> {

        Serializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant value, JsonGenerator jgen, SerializationContext provider) {
            jgen.writeString(ISO_INSTANT.format(value));
        }
    }

    static final class Deserializer extends StdDeserializer<Instant> {

        Deserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) {
            String text = p.getString();
            try {
                return Instant.from(ISO_INSTANT.parse(text));
            } catch (DateTimeException e) {
                throw new InputCoercionException(p, "Expected an ISO 8601 formatted date time", p.currentToken(), Instant.class);
            }
        }
    }
}
