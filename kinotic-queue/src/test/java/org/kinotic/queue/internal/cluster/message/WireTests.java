package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that queue messages carry the version of their encoding, and that a node refuses a message of another version.
 */
public class WireTests {

    @Test
    public void aMessageReadsBackAsWritten() {
        FetchRequest request = new FetchRequest("orders", 3, 42, 256, 1024);

        assertEquals(request, FetchRequest.fromBuffer(request.toBuffer()));
    }

    @Test
    public void aMessageOfAnotherEncodingVersionIsRefused() {
        Buffer message = new FetchRequest("orders", 3, 42, 256, 1024).toBuffer();
        message.setByte(0, (byte) (message.getByte(0) + 1));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> FetchRequest.fromBuffer(message));
        assertTrue(e.getMessage().contains("every queue node must run the same version"));
    }
}
