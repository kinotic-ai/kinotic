package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.ShardLog;

import java.util.Map;

/**
 * How far a node's copy of a shard reaches.
 *
 * @param nextOffset      the offset after the copy's last entry; zero when the node holds no copy
 * @param lastEpoch       the epoch of the copy's last entry; -1 when the copy is empty
 * @param acceptedEpoch   the newest epoch the node had promised for the shard before the prepare this status answers;
 *                        -1 when it had promised none
 * @param consumerOffsets the next offset of every consumer that has committed on the shard, by consumer name
 * @param groupOffsets    the low watermark of every worker group that has finished records on the shard, by group name
 */
public record ShardStatus(long nextOffset,
                          long lastEpoch,
                          long acceptedEpoch,
                          Map<String, Long> consumerOffsets,
                          Map<String, Long> groupOffsets) {

    /**
     * @param acceptedEpoch the newest epoch the copy had promised before the prepare this status answers
     */
    public static ShardStatus of(ShardLog shardLog, long acceptedEpoch, Map<String, Long> consumerOffsets, Map<String, Long> groupOffsets) {
        return new ShardStatus(shardLog.nextOffset(), shardLog.lastEpoch(), acceptedEpoch, consumerOffsets, groupOffsets);
    }

    /**
     * The status of a node that holds no copy of the shard.
     */
    public static ShardStatus none(long acceptedEpoch) {
        return new ShardStatus(0, -1, acceptedEpoch, Map.of(), Map.of());
    }

    public static ShardStatus fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ShardStatus(wire.readLong(), wire.readLong(), wire.readLong(), wire.readOffsets(), wire.readOffsets());
    }

    /**
     * @return whether this copy holds entries the other may lack: a newer last epoch, or more entries in the same one
     */
    public boolean isAheadOf(ShardStatus other) {
        return lastEpoch > other.lastEpoch || (lastEpoch == other.lastEpoch && nextOffset > other.nextOffset);
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.appendOffsets(Buffer.buffer().appendLong(nextOffset).appendLong(lastEpoch).appendLong(acceptedEpoch),
                                        consumerOffsets);
        return Wire.appendOffsets(ret, groupOffsets);
    }
}
