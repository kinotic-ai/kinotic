package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;
import org.kinotic.queue.api.model.StartPosition;

/**
 * Asks a shard's owner to lease records of the shard to one worker of a group. The reply is a {@link LeaseResponse}.
 * The {@code incarnation} names the queue the request is about, so a request about a deleted queue is never applied to a
 * queue created later with the same name.
 *
 * @param workerId      identifies the worker the records are leased to
 * @param max           the most records to lease
 * @param leaseMillis   how long the worker holds the records before they go back to the group
 * @param startPosition where the group starts when it has no position on the shard
 */
public record LeaseRequest(String queue,
                           String incarnation,
                           int shard,
                           String groupName,
                           String workerId,
                           int max,
                           long leaseMillis,
                           StartPosition startPosition) {

    public static LeaseRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        return new LeaseRequest(wire.readString(),
                                wire.readString(),
                                wire.readInt(),
                                wire.readString(),
                                wire.readString(),
                                wire.readInt(),
                                wire.readLong(),
                                StartPosition.values()[wire.readInt()]);
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.appendString(Wire.appendString(Wire.buffer(), queue), incarnation).appendInt(shard);
        Wire.appendString(ret, groupName);
        Wire.appendString(ret, workerId);
        return ret.appendInt(max).appendLong(leaseMillis).appendInt(startPosition.ordinal());
    }
}
