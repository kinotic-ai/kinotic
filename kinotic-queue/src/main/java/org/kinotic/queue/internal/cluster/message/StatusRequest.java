package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a queue node whether it owns a shard. The reply is an {@link OwnerStatus}.
 */
public record StatusRequest(String queue, int shard) {

    public static StatusRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new StatusRequest(wire.readString(), wire.readInt());
    }

    public Buffer toBuffer() {
        return Wire.appendString(Wire.buffer(), queue).appendInt(shard);
    }
}
