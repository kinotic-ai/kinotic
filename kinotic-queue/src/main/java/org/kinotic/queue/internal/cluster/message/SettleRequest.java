package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * Tells a shard's owner what a worker did with a record leased to it. The reply is empty; the owner fails the
 * request when the record is not leased to the worker, or was leased to it again since the delivery it settles.
 *
 * @param deliveryCount the delivery the worker settles, as the {@link LeasedEntry#deliveryCount lease} gave it
 */
public record SettleRequest(ShardId shardId, String groupName, String workerId, Settlement settlement, long offset, int deliveryCount) {

    public static SettleRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new SettleRequest(wire.readShardId(),
                                 wire.readString(),
                                 wire.readString(),
                                 Settlement.values()[wire.readInt()],
                                 wire.readLong(),
                                 wire.readInt());
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.appendShardId(Wire.buffer(), shardId);
        Wire.appendString(ret, groupName);
        Wire.appendString(ret, workerId);
        return ret.appendInt(settlement.ordinal()).appendLong(offset).appendInt(deliveryCount);
    }
}
