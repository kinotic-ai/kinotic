package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Asks a shard's owner to append a record. The reply is the record's offset.
 */
public record AppendRequest(String queue, int shard, String key, byte[] payload) {

    public static AppendRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new AppendRequest(wire.readString(), wire.readInt(), wire.readString(), wire.readBytes());
    }

    public static Buffer encodeReply(long offset) {
        return Wire.buffer().appendLong(offset);
    }

    public static long decodeReply(Buffer buffer) {
        return new Wire(buffer).readLong();
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.buffer();
        Wire.appendString(ret, queue).appendInt(shard);
        Wire.appendString(ret, key);
        Wire.appendBytes(ret, payload);
        return ret;
    }
}
