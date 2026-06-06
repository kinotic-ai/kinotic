

package org.kinotic.core.internal.api.event;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.Metadata;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link EventMessageCodec}. A plain (non domain-aware) mapper is enough here since
 * these cases carry no sender; the polymorphic sender path is exercised by full-context integration tests.
 *
 * Created by Navid Mitchell on 2026-06-06.
 */
public class EventMessageCodecTests {

    private final EventMessageCodec codec = new EventMessageCodec(JsonMapper.builder().build());

    @Test
    public void roundTripsEnvelopeAndDataOverTheWire() {
        Metadata metadata = Metadata.create();
        metadata.put(EventConstants.CORRELATION_ID_HEADER, "abc-123");
        metadata.put(EventConstants.CONTENT_TYPE_HEADER, "application/json");
        byte[] payload = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
        Event<byte[]> original = Event.create(CRI.create("srv://org.example.FooService/bar#1.0.0"), metadata, payload);

        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, original);
        Event<byte[]> decoded = codec.decodeFromWire(0, buffer);

        assertEquals(original.cri().raw(), decoded.cri().raw());
        assertArrayEquals(payload, decoded.data());
        assertEquals("abc-123", decoded.metadata().get(EventConstants.CORRELATION_ID_HEADER));
        assertEquals("application/json", decoded.metadata().get(EventConstants.CONTENT_TYPE_HEADER));
        assertNull(decoded.sender());
    }

    @Test
    public void roundTripsNullData() {
        Event<byte[]> original = Event.create(CRI.create("srv://org.example.FooService#1.0.0"), Metadata.create(), null);

        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, original);
        Event<byte[]> decoded = codec.decodeFromWire(0, buffer);

        assertNull(decoded.data());
        assertEquals(original.cri().raw(), decoded.cri().raw());
    }

    @Test
    public void transformIsZeroCopyForLocalDelivery() {
        Event<byte[]> original = Event.create(CRI.create("srv://org.example.FooService#1.0.0"), Metadata.create(), new byte[0]);
        assertSame(original, codec.transform(original));
    }
}
