package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a node for the {@link ShardStatus} of a shard it may hold.
 */
public record ShardRequest(String queue, int shard) {

    public static ShardRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ShardRequest(wire.readString(), wire.readInt());
    }

    public Buffer toBuffer() {
        return Wire.appendString(Buffer.buffer(), queue).appendInt(shard);
    }
}
