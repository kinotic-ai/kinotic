package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

/**
 * How far a node's copy of a shard reaches.
 *
 * @param nextOffset    the offset after the copy's last entry; zero when the node holds no copy
 * @param lastEpoch     the epoch of the copy's last entry; -1 when it holds none
 * @param acceptedEpoch the newest owner epoch the copy has accepted; -1 when the node holds no copy
 */
public record ShardStatus(long nextOffset, long lastEpoch, long acceptedEpoch) {

    /**
     * The status of a node that holds no copy of the shard.
     */
    public static final ShardStatus NONE = new ShardStatus(0, -1, -1);

    public static ShardStatus fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ShardStatus(wire.readLong(), wire.readLong(), wire.readLong());
    }

    /**
     * @return whether this copy holds entries the other does not: a newer last epoch, or more entries in the same one
     */
    public boolean isAheadOf(ShardStatus other) {
        return lastEpoch > other.lastEpoch || (lastEpoch == other.lastEpoch && nextOffset > other.nextOffset);
    }

    public Buffer toBuffer() {
        return Buffer.buffer().appendLong(nextOffset).appendLong(lastEpoch).appendLong(acceptedEpoch);
    }
}
