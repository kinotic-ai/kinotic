package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.internal.log.LogStart;
import org.kinotic.queue.internal.log.QueueLog;
import org.kinotic.queue.internal.log.ShardEntry;
import org.kinotic.queue.internal.log.ShardLog;
import org.kinotic.queue.internal.log.ShardPositions;

import java.util.List;

/**
 * How far a node's copy of a shard reaches.
 *
 * @param start         where the copy's entries begin
 * @param nextOffset    the offset after the copy's last entry; zero when the node holds no copy
 * @param lastEpoch     the epoch of the copy's last entry; -1 when the copy is empty
 * @param acceptedEpoch the newest epoch the node had promised for the shard before the prepare this status answers;
 *                      -1 when it had promised none
 * @param positions     the positions of the consumers and worker groups on the shard that have not expired
 * @param membership    the last membership change the copy holds; null when it holds none
 */
public record ShardStatus(LogStart start,
                          long nextOffset,
                          long lastEpoch,
                          long acceptedEpoch,
                          ShardPositions positions,
                          ShardEntry membership) {

    /**
     * Has this node's copy of the shard promise the epoch, which it then refuses entries from any older owner for.
     * Blocks on disk access.
     *
     * @return the status of the copy, or of no copy when the node holds none
     */
    public static ShardStatus prepare(QueueLog queueLog, int shard, long epoch) {
        long acceptedEpoch = queueLog.promise(shard, epoch);
        ShardLog shardLog = queueLog.findShard(shard);
        return shardLog != null
                ? new ShardStatus(shardLog.start(), shardLog.nextOffset(), shardLog.lastEpoch(), acceptedEpoch, queueLog.positions(shard),
                                  shardLog.latestMembership())
                : new ShardStatus(new LogStart(0, -1), 0, -1, acceptedEpoch, ShardPositions.NONE, null);
    }

    public static ShardStatus fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new ShardStatus(new LogStart(wire.readLong(), wire.readLong()), wire.readLong(), wire.readLong(), wire.readLong(),
                               wire.readPositions(), wire.readEntries().stream().findFirst().orElse(null));
    }

    /**
     * @return whether this copy holds entries the other may lack: a newer last epoch, or more entries in the same one
     */
    public boolean isAheadOf(ShardStatus other) {
        return lastEpoch > other.lastEpoch || (lastEpoch == other.lastEpoch && nextOffset > other.nextOffset);
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.buffer()
                         .appendLong(start.offset())
                         .appendLong(start.epochBefore())
                         .appendLong(nextOffset)
                         .appendLong(lastEpoch)
                         .appendLong(acceptedEpoch);
        return Wire.appendEntries(Wire.appendPositions(ret, positions), membership != null ? List.of(membership) : List.of());
    }
}
