package org.kinotic.vertx.jackson3;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.time.Instant;

public class TestEnvelope {

    private JsonObject payload;
    private byte[] raw;
    private Buffer buffer;
    private Instant when;

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }

    public byte[] getRaw() {
        return raw;
    }

    public void setRaw(byte[] raw) {
        this.raw = raw;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public void setBuffer(Buffer buffer) {
        this.buffer = buffer;
    }

    public Instant getWhen() {
        return when;
    }

    public void setWhen(Instant when) {
        this.when = when;
    }
}
