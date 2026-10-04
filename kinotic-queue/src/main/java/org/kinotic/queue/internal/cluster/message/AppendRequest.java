package org.kinotic.queue.internal.cluster.message;

import io.vertx.core.buffer.Buffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks a shard's owner to append a batch of records. A batch sent again with the same producer and sequence is
 * written once. The reply is the offset of each record, in the order of {@code records}.
 *
 * @param producerId identifies the client that sent the batch, for as long as that client runs
 * @param sequence   the batch's number among the batches the client sent to the shard; increases with every batch
 */
public record AppendRequest(String queue, int shard, long producerId, long sequence, List<AppendRecord> records) {

    public static AppendRequest fromBuffer(Buffer buffer) {
        Wire wire = new Wire(buffer);
        String queue = wire.readString();
        int shard = wire.readInt();
        long producerId = wire.readLong();
        long sequence = wire.readLong();
        int count = wire.readInt();
        List<AppendRecord> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            records.add(new AppendRecord(wire.readString(), wire.readBytes()));
        }
        return new AppendRequest(queue, shard, producerId, sequence, records);
    }

    public static Buffer encodeReply(List<Long> offsets) {
        Buffer ret = Wire.buffer().appendInt(offsets.size());
        offsets.forEach(ret::appendLong);
        return ret;
    }

    public static List<Long> decodeReply(Buffer buffer) {
        Wire wire = new Wire(buffer);
        int count = wire.readInt();
        List<Long> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ret.add(wire.readLong());
        }
        return ret;
    }

    public Buffer toBuffer() {
        Buffer ret = Wire.appendString(Wire.buffer(), queue).appendInt(shard).appendLong(producerId).appendLong(sequence)
                         .appendInt(records.size());
        for (AppendRecord record : records) {
            Wire.appendString(ret, record.key());
            Wire.appendBytes(ret, record.payload());
        }
        return ret;
    }
}
