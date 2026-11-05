package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.ReplicationBatch;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ReplicationStatus;
import org.kinotic.queue.internal.log.ShardPositions;

/**
 * Carries a batch of entries from a shard's owner to a follower. The reply is a {@link ReplicationResult}.
 *
 * @param positions the positions the owner stores for the shard when they changed since the follower last received
 *                  them, otherwise none
 */
public record ReplicateRequest(ShardId shardId, ReplicationBatch batch, ShardPositions positions) {

    public static ReplicateRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ReplicateRequest(wire.readShardId(),
                                    new ReplicationBatch(wire.readLong(),
                                                         wire.readLong(),
                                                         wire.readLong(),
                                                         wire.readLong(),
                                                         wire.readLong(),
                                                         wire.readEntries()),
                                    wire.readPositions());
    }

    public static Buffer encodeReply(ReplicationResult result) {
        return Wire.buffer().appendInt(result.status().ordinal()).appendLong(result.nextOffset()).appendLong(result.conflictEpoch());
    }

    public static ReplicationResult decodeReply(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ReplicationResult(ReplicationStatus.values()[wire.readInt()], wire.readLong(), wire.readLong());
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.appendShardId(Wire.buffer(batch.entries()), shardId)
                         .appendLong(batch.epoch())
                         .appendLong(batch.prevOffset())
                         .appendLong(batch.prevEpoch())
                         .appendLong(batch.ownerStartOffset())
                         .appendLong(batch.ownerNextOffset());
        return Wire.appendPositions(Wire.appendEntries(ret, batch.entries()), positions);
    }
}
