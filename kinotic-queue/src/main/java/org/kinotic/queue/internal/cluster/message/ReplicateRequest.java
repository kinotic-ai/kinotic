package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.ReplicationBatch;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ReplicationStatus;

import java.util.Map;

/**
 * Carries a batch of entries from a shard's owner to a follower, and the consumer and group offsets the owner stores
 * for the shard when they changed since the follower last received them. The reply is a {@link ReplicationResult}.
 * The {@code incarnation} names the queue the request is about, so a request about a deleted queue is never applied to a
 * queue created later with the same name.
 */
public record ReplicateRequest(String queue,
                               String incarnation,
                               int shard,
                               ReplicationBatch batch,
                               Map<String, Long> consumerOffsets,
                               Map<String, Long> groupOffsets) {

    public static ReplicateRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ReplicateRequest(wire.readString(),
                                    wire.readString(),
                                    wire.readInt(),
                                    new ReplicationBatch(wire.readLong(),
                                                         wire.readLong(),
                                                         wire.readLong(),
                                                         wire.readLong(),
                                                         wire.readLong(),
                                                         wire.readEntries()),
                                    wire.readOffsets(),
                                    wire.readOffsets());
    }

    public static Buffer encodeReply(ReplicationResult result) {
        return Wire.buffer().appendInt(result.status().ordinal()).appendLong(result.nextOffset()).appendLong(result.conflictEpoch());
    }

    public static ReplicationResult decodeReply(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ReplicationResult(ReplicationStatus.values()[wire.readInt()], wire.readLong(), wire.readLong());
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.buffer();
        Wire.appendString(Wire.appendString(ret, queue), incarnation)
            .appendInt(shard)
            .appendLong(batch.epoch())
            .appendLong(batch.prevOffset())
            .appendLong(batch.prevEpoch())
            .appendLong(batch.ownerStartOffset())
            .appendLong(batch.ownerNextOffset());
        return Wire.appendOffsets(Wire.appendOffsets(Wire.appendEntries(ret, batch.entries()), consumerOffsets), groupOffsets);
    }
}
