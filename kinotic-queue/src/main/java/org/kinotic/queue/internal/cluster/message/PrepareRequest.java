package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Sent by a node about to own a shard to every queue node. A node holding a copy promises not to accept entries
 * from an owner older than {@code epoch}, and replies with the {@link ShardStatus} of its copy.
 * The {@code incarnation} names the queue the request is about, so a request about a deleted queue is never applied to a
 * queue created later with the same name.
 */
public record PrepareRequest(String queue, String incarnation, int shard, long epoch) {

    public static PrepareRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new PrepareRequest(wire.readString(), wire.readString(), wire.readInt(), wire.readLong());
    }

    public Buffer toBuffer() {
        return Wire.appendString(Wire.appendString(Wire.buffer(), queue), incarnation).appendInt(shard).appendLong(epoch);
    }
}
