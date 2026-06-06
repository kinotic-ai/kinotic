package org.kinotic.core.internal.api.event;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.DefaultEvent;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.Metadata;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vert.x {@link MessageCodec} for {@link Event} objects sent on the event bus.
 * <p>
 * Local delivery is zero-copy: {@link #transform} hands the same {@link Event} instance to the local
 * handler, so a co-located service invocation never serializes. Only remote delivery serializes — the
 * {@code {cri, sender, metadata}} envelope is written as Jackson JSON (so {@code sender} resolves to the
 * correct {@code Participant} subtype) and the already-encoded payload bytes are written verbatim,
 * length-prefixed, so the payload is neither re-encoded nor base64-inflated.
 */
public class EventMessageCodec implements MessageCodec<Event<byte[]>, Event<byte[]>> {

    public static final String NAME = "kinotic-event";

    private final JsonMapper jsonMapper;

    public EventMessageCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void encodeToWire(Buffer buffer, Event<byte[]> event) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : event.metadata()) {
            metadata.put(entry.getKey(), entry.getValue());
        }
        byte[] envelope = jsonMapper.writeValueAsBytes(new EventEnvelope(event.cri().raw(), event.sender(), metadata));
        buffer.appendInt(envelope.length);
        buffer.appendBytes(envelope);

        byte[] data = event.data();
        if (data == null) {
            buffer.appendInt(-1);
        } else {
            buffer.appendInt(data.length);
            buffer.appendBytes(data);
        }
    }

    @Override
    public Event<byte[]> decodeFromWire(int pos, Buffer buffer) {
        int envelopeLength = buffer.getInt(pos);
        pos += 4;
        EventEnvelope envelope = jsonMapper.readValue(buffer.getBytes(pos, pos + envelopeLength), EventEnvelope.class);
        pos += envelopeLength;

        int dataLength = buffer.getInt(pos);
        pos += 4;
        byte[] data = dataLength < 0 ? null : buffer.getBytes(pos, pos + dataLength);

        Map<String, String> metadata = envelope.metadata() != null ? envelope.metadata() : new LinkedHashMap<>();
        DefaultEvent<byte[]> event = new DefaultEvent<>(CRI.create(envelope.cri()), Metadata.create(metadata), data);
        event.setSender(envelope.sender());
        return event;
    }

    @Override
    public Event<byte[]> transform(Event<byte[]> event) {
        // Local delivery: hand the same instance over, no serialization.
        return event;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
