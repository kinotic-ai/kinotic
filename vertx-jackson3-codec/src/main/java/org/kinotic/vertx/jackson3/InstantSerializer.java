/*
 * Derived from Eclipse Vert.x (io.vertx.core.json.jackson.v3).
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.kinotic.vertx.jackson3;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.time.Instant;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

class InstantSerializer extends StdSerializer<Instant> {

    InstantSerializer() {
        super(Instant.class);
    }

    @Override
    public void serialize(Instant value, JsonGenerator jgen, SerializationContext provider) {
        jgen.writeString(ISO_INSTANT.format(value));
    }
}
