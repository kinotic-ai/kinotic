package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.ReplicationResult;
import org.kinotic.queue.internal.log.ReplicationStatus;
import org.kinotic.queue.internal.log.ShardEntry;

import java.util.List;
import java.util.Map;

/**
 * Carries a batch of entries from a shard's owner to a follower; the arguments of
 * {@link org.kinotic.queue.internal.log.ShardLog#replicate}, and the consumer and group offsets the owner stores for
 * the shard when they changed since the follower last received them. The reply is a {@link ReplicationResult}.
 */
public record ReplicateRequest(String queue,
                               int shard,
                               long epoch,
                               long prevOffset,
                               long prevEpoch,
                               long ownerNextOffset,
                               List<ShardEntry> entries,
                               Map<String, Long> consumerOffsets,
                               Map<String, Long> groupOffsets) {

    public static ReplicateRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ReplicateRequest(wire.readString(),
                                    wire.readInt(),
                                    wire.readLong(),
                                    wire.readLong(),
                                    wire.readLong(),
                                    wire.readLong(),
                                    wire.readEntries(),
                                    wire.readOffsets(),
                                    wire.readOffsets());
    }

    public static Buffer encodeReply(ReplicationResult result) {
        return Wire.buffer().appendInt(result.status().ordinal()).appendLong(result.nextOffset());
    }

    public static ReplicationResult decodeReply(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ReplicationResult(ReplicationStatus.values()[wire.readInt()], wire.readLong());
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.buffer();
        Wire.appendString(ret, queue)
            .appendInt(shard)
            .appendLong(epoch)
            .appendLong(prevOffset)
            .appendLong(prevEpoch)
            .appendLong(ownerNextOffset);
        return Wire.appendOffsets(Wire.appendOffsets(Wire.appendEntries(ret, entries), consumerOffsets), groupOffsets);
    }
}
