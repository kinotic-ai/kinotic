package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Tells a shard's owner what a worker did with a record leased to it. The reply is empty; the owner fails the
 * request when the record is not leased to the worker.
 * The {@code incarnation} names the queue the request is about, so a request about a deleted queue is never applied to a
 * queue created later with the same name.
 */
public record SettleRequest(String queue, String incarnation, int shard, String groupName, String workerId, Settlement settlement, long offset) {

    public static SettleRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new SettleRequest(wire.readString(),
                                 wire.readString(),
                                 wire.readInt(),
                                 wire.readString(),
                                 wire.readString(),
                                 Settlement.values()[wire.readInt()],
                                 wire.readLong());
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.appendString(Wire.appendString(Wire.buffer(), queue), incarnation).appendInt(shard);
        Wire.appendString(ret, groupName);
        Wire.appendString(ret, workerId);
        return ret.appendInt(settlement.ordinal()).appendLong(offset);
    }
}
