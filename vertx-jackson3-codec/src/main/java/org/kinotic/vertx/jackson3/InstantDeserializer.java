/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3).
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.time.DateTimeException;
import java.time.Instant;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

class InstantDeserializer extends StdDeserializer<Instant> {

    InstantDeserializer() {
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
