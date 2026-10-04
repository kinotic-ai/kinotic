package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a queue node whether it owns a shard. The reply is an {@link OwnerStatus}.
 * The {@code incarnation} names the queue the request is about, so a request about a deleted queue is never applied to a
 * queue created later with the same name.
 */
public record StatusRequest(String queue, String incarnation, int shard) {

    public static StatusRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new StatusRequest(wire.readString(), wire.readString(), wire.readInt());
    }

    public Buffer toBuffer() {
        return Wire.appendString(Wire.appendString(Wire.buffer(), queue), incarnation).appendInt(shard);
    }
}
