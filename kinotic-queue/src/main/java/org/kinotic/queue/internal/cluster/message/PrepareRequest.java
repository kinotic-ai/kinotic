package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Sent by a node about to own a shard to every queue node. A node holding a copy promises not to accept entries
 * from an owner older than {@code epoch}, and replies with the {@link ShardStatus} of its copy.
 */
public record PrepareRequest(String queue, int shard, long epoch) {

    public static PrepareRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new PrepareRequest(wire.readString(), wire.readInt(), wire.readLong());
    }

    public Buffer toBuffer() {
        return Wire.appendString(Buffer.buffer(), queue).appendInt(shard).appendLong(epoch);
    }
}
